#include "chat_runtime.h"
#include <nlohmann/json.hpp>
#include <fstream>
#include <iostream>
#include <memory>
using json = nlohmann::ordered_json;
int main(int argc, char **argv) {
    if (argc != 4) return 2;
    try {
        llama_backend_init();
        auto mp = llama_model_default_params(); mp.n_gpu_layers = 0;
        std::unique_ptr<llama_model, decltype(&llama_model_free)> model(llama_model_load_from_file(argv[1], mp), llama_model_free);
        if (!model) throw std::runtime_error("GGUF load failed");
        const auto *vocab = llama_model_get_vocab(model.get());
        atlas::ChatRenderer renderer(model.get(), false);
        std::ifstream input(argv[2]); json requests; input >> requests;
        json results = json::array();
        for (const auto &request : requests) {
            std::vector<std::string> roles, contents;
            for (const auto &msg : request.at("messages")) { roles.push_back(msg.at("role")); contents.push_back(msg.at("content")); }
            const auto rendered = renderer.render(roles, contents, request.at("thinking"));
            int n = llama_tokenize(vocab, rendered.prompt.data(), rendered.prompt.size(), nullptr, 0, true, true);
            if (n >= 0) throw std::runtime_error("Tokenization length failed");
            std::vector<llama_token> tokens(-n);
            n = llama_tokenize(vocab, rendered.prompt.data(), rendered.prompt.size(), tokens.data(), tokens.size(), true, true);
            if (n <= 0) throw std::runtime_error("Tokenization failed"); tokens.resize(n);
            const bool double_bos = tokens.size() > 1 && tokens[0] == llama_vocab_bos(vocab) && tokens[1] == llama_vocab_bos(vocab);
            if (double_bos) throw std::runtime_error("Duplicate BOS");
            auto cp = llama_context_default_params(); cp.n_ctx = 2048; cp.n_batch = 128; cp.n_ubatch = 32; cp.n_threads = 2; cp.n_threads_batch = 2;
            std::unique_ptr<llama_context, decltype(&llama_free)> ctx(llama_init_from_model(model.get(), cp), llama_free);
            if (!ctx) throw std::runtime_error("Context allocation failed");
            auto c = request.at("sampling");
            atlas::Sampling sampling{c.at("temperature"), c.at("topP"), c.at("topK"), c.at("minP"), c.at("repeatPenalty")};
            std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(atlas::make_sampler(sampling, 42), llama_sampler_free);
            for (size_t i = 0; i < tokens.size(); i += cp.n_batch) {
                int count = std::min<size_t>(cp.n_batch, tokens.size() - i);
                if (llama_decode(ctx.get(), llama_batch_get_one(tokens.data()+i, count))) throw std::runtime_error("Prefill failed");
            }
            for (auto token : tokens) llama_sampler_accept(sampler.get(), token);
            std::string raw; bool eog = false; int generated = 0;
            const int budget = request.value("maxTokens", 256);
            if (tokens.size() + budget + 8 > cp.n_ctx) throw std::runtime_error("Context exceeds app limit");
            for (; generated < budget; ++generated) {
                auto token = llama_sampler_sample(sampler.get(), ctx.get(), -1);
                llama_sampler_accept(sampler.get(), token);
                if (llama_vocab_is_eog(vocab, token)) { eog = true; break; }
                raw += atlas::piece(vocab, token);
                if (llama_decode(ctx.get(), llama_batch_get_one(&token, 1))) throw std::runtime_error("Decode failed");
            }
            results.push_back({{"id", request.at("id")}, {"prompt", rendered.prompt}, {"raw", raw}, {"forcedOpen", rendered.thinking_open},
                {"reasoning", renderer.reasoning}, {"thinkingSwitch", renderer.supports_thinking}, {"thinking", request.at("thinking")},
                {"tokens", generated}, {"eog", eog}, {"doubleBos", double_bos}, {"sampling", c}, {"architecture", [&] { char value[128] = {}; llama_model_meta_val_str(model.get(), "general.architecture", value, sizeof(value)); return std::string(value); }()},
                {"name", [&] { char value[512] = {}; llama_model_meta_val_str(model.get(), "general.name", value, sizeof(value)); return std::string(value); }()}});
            std::ofstream(argv[3]) << results.dump(2);
        }
        return 0;
    } catch (const std::exception &e) { std::cerr << e.what() << '\n'; return 1; }
}
