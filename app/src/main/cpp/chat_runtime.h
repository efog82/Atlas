#pragma once
#include <llama.h>
#include <chat.h>
#include <minja/chat-template.hpp>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

namespace atlas {
inline std::string piece(const llama_vocab *vocab, llama_token token) {
    if (token == LLAMA_TOKEN_NULL) return {};
    std::vector<char> bytes(256);
    int n = llama_token_to_piece(vocab, token, bytes.data(), bytes.size(), 0, true);
    if (n < 0) { bytes.resize(-n); n = llama_token_to_piece(vocab, token, bytes.data(), bytes.size(), 0, true); }
    if (n < 0) throw std::runtime_error("Token GGUF invalido.");
    return std::string(bytes.data(), n);
}
struct RenderedChat { std::string prompt; bool thinking_open = false; };
class ChatRenderer {
    const llama_vocab *vocab;
    common_chat_templates_ptr templates;
public:
    bool manual = false, supports_thinking = false, reasoning = false;
    std::string source;
    ChatRenderer(const llama_model *model, bool allow_chatml) : vocab(llama_model_get_vocab(model)) {
        const char *raw = llama_model_chat_template(model, nullptr);
        if (!raw || !*raw) {
            if (!allow_chatml) throw std::runtime_error("GGUF sem chat template. Importa uma versao Instruct com template ou escolhe explicitamente ChatML nas definicoes.");
            source = "chatml"; manual = true;
        } else source = raw;
        // Upstream falls back to ChatML on a Jinja parse error. Validate first: never silently substitute it.
        if (source != "chatml") {
            minja::chat_template strict(source, piece(vocab, llama_vocab_bos(vocab)), piece(vocab, llama_vocab_eos(vocab)));
        }
        templates = common_chat_templates_init(model, manual ? "chatml" : "");
        if (!manual && source != "chatml" && source.find("<|channel|>") == std::string::npos && source != common_chat_templates_source(templates.get()))
            throw std::runtime_error("O template Jinja nao foi preservado. Usa outro GGUF compativel.");
        reasoning = source.find("<think>") != std::string::npos;
        try { supports_thinking = common_chat_templates_support_enable_thinking(templates.get()); } catch (...) { supports_thinking = false; }
    }
    RenderedChat render(const std::vector<std::string> &roles, const std::vector<std::string> &contents, bool thinking) const {
        if (roles.empty() || roles.size() != contents.size() || roles.back() != "user") throw std::runtime_error("Conversa invalida.");
        common_chat_templates_inputs inputs;
        inputs.use_jinja = true; inputs.enable_thinking = thinking; inputs.add_generation_prompt = true;
        // common removes template BOS/EOS when the tokenizer is going to add them.
        inputs.add_bos = llama_vocab_get_add_bos(vocab); inputs.add_eos = llama_vocab_get_add_eos(vocab);
        inputs.reasoning_format = COMMON_REASONING_FORMAT_NONE;
        for (size_t i = 0; i < roles.size(); ++i) {
            if (roles[i] != "system" && roles[i] != "user" && roles[i] != "assistant") throw std::runtime_error("Papel de conversa invalido.");
            common_chat_msg msg; msg.role = roles[i]; msg.content = contents[i]; inputs.messages.push_back(msg);
        }
        auto result = common_chat_templates_apply(templates.get(), inputs);
        auto tail = result.prompt;
        while (!tail.empty() && (tail.back() == '\n' || tail.back() == '\r' || tail.back() == ' ' || tail.back() == '\t')) tail.pop_back();
        const bool forced = result.thinking_forced_open || (tail.size() >= 7 && tail.compare(tail.size()-7, 7, "<think>") == 0);
        return {result.prompt, forced};
    }
};
struct Sampling {
    float temperature, top_p; int top_k; float min_p, repeat_penalty;
};
inline llama_sampler *make_sampler(const Sampling &config, uint32_t seed = LLAMA_DEFAULT_SEED) {
    if (config.temperature < 0 || config.temperature > 2 || config.top_p <= 0 || config.top_p > 1 || config.top_k < 1 || config.top_k > 200 ||
        config.min_p < 0 || config.min_p > 1 || config.repeat_penalty < 1 || config.repeat_penalty > 1.5)
        throw std::runtime_error("Amostragem invalida.");
    auto sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (config.repeat_penalty > 1) llama_sampler_chain_add(sampler, llama_sampler_init_penalties(64, config.repeat_penalty, 0, 0));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(config.top_k));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(config.top_p, 1));
    if (config.min_p > 0) llama_sampler_chain_add(sampler, llama_sampler_init_min_p(config.min_p, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(config.temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(seed));
    return sampler;
}
}
