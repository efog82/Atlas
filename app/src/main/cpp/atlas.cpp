#include <jni.h>
#include <llama.h>
#include <atomic>
#include <vector>
#include <string>
#include <stdexcept>
#include <algorithm>
#include <codecvt>
#include <locale>
#include <new>

// All methods except stop are serialized by LocalEngine. stop only touches an atomic flag.
namespace {
llama_model * model = nullptr;
llama_context * ctx = nullptr;
llama_sampler * sampler = nullptr;
const llama_vocab * vocab = nullptr;
std::atomic<bool> stopped{false};
std::vector<llama_token> input;
int position = 0;
int generated = 0;
int token_limit = 0;

std::string utf8(JNIEnv *env, jstring value) {
    if (!value) return {};
    const jchar * chars = env->GetStringChars(value, nullptr);
    std::u16string text(reinterpret_cast<const char16_t *>(chars), env->GetStringLength(value));
    env->ReleaseStringChars(value, chars);
    return std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t>().to_bytes(text);
}
void fail(JNIEnv *env, const std::string &message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message.c_str());
}
void end_generation() {
    if (sampler) { llama_sampler_free(sampler); sampler = nullptr; }
    if (ctx) { llama_free(ctx); ctx = nullptr; }
    input.clear();
}
std::vector<llama_token> tokenize(const std::string &text) {
    int n = llama_tokenize(vocab, text.data(), static_cast<int>(text.size()), nullptr, 0, true, true);
    if (n >= 0) return {};
    std::vector<llama_token> tokens(-n);
    n = llama_tokenize(vocab, text.data(), static_cast<int>(text.size()), tokens.data(), tokens.size(), true, true);
    if (n < 0) throw std::runtime_error("Falha ao tokenizar o contexto.");
    tokens.resize(n);
    return tokens;
}
std::string render(const std::vector<std::string> &roles, const std::vector<std::string> &contents) {
    std::vector<llama_chat_message> messages;
    for (size_t i = 0; i < roles.size(); ++i) messages.push_back({roles[i].c_str(), contents[i].c_str()});
    const char *tmpl = llama_model_chat_template(model, nullptr);
    if (!tmpl) tmpl = "chatml";
    std::vector<char> buf(4096);
    int n = llama_chat_apply_template(tmpl, messages.data(), messages.size(), true, buf.data(), buf.size());
    if (n < 0) throw std::runtime_error("Template de conversa GGUF nao suportado.");
    if (n > static_cast<int>(buf.size())) {
        buf.resize(n + 1);
        n = llama_chat_apply_template(tmpl, messages.data(), messages.size(), true, buf.data(), buf.size());
    }
    if (n < 0) throw std::runtime_error("Template invalido.");
    return std::string(buf.data(), n);
}
}

extern "C" JNIEXPORT void JNICALL
Java_pt_atlas_personal_ai_LocalEngine_nativeLoad(JNIEnv *env, jobject, jstring path) {
    try {
        end_generation();
        if (model) { llama_model_free(model); model = nullptr; vocab = nullptr; }
        llama_backend_init();
        auto params = llama_model_default_params();
        params.n_gpu_layers = 0;
        params.use_mmap = true;
        params.use_mlock = false;
        model = llama_model_load_from_file(utf8(env, path).c_str(), params);
        if (!model) throw std::runtime_error("Nao foi possivel carregar o GGUF. Verifica compatibilidade e RAM disponivel.");
        vocab = llama_model_get_vocab(model);
    } catch (const std::exception &e) { fail(env, e.what()); }
}

extern "C" JNIEXPORT void JNICALL
Java_pt_atlas_personal_ai_LocalEngine_nativeUnload(JNIEnv *, jobject) {
    end_generation();
    if (model) { llama_model_free(model); model = nullptr; vocab = nullptr; }
}

extern "C" JNIEXPORT void JNICALL
Java_pt_atlas_personal_ai_LocalEngine_nativeBegin(JNIEnv *env, jobject, jobjectArray jroles,
        jobjectArray jcontents, jint context_tokens, jint max_tokens, jint threads) {
    try {
        if (!model) throw std::runtime_error("Instala e carrega um modelo GGUF primeiro.");
        end_generation();
        generated = 0; position = 0;
        token_limit = std::min(static_cast<int>(max_tokens), static_cast<int>(context_tokens) / 2);
        std::vector<std::string> roles, contents;
        const int count = env->GetArrayLength(jroles);
        if (count == 0 || count != env->GetArrayLength(jcontents)) throw std::runtime_error("Conversa invalida.");
        for (int i = 0; i < count; ++i) {
            auto role = static_cast<jstring>(env->GetObjectArrayElement(jroles, i));
            auto content = static_cast<jstring>(env->GetObjectArrayElement(jcontents, i));
            roles.push_back(utf8(env, role)); contents.push_back(utf8(env, content));
            env->DeleteLocalRef(role); env->DeleteLocalRef(content);
        }
        input = tokenize(render(roles, contents));
        // Discard complete old turns, never a random token prefix or the system message.
        while (input.size() + token_limit + 8 > static_cast<size_t>(context_tokens) && roles.size() > 2) {
            roles.erase(roles.begin() + 1); contents.erase(contents.begin() + 1);
            if (roles.size() > 2 && roles[1] == "assistant") {
                roles.erase(roles.begin() + 1); contents.erase(contents.begin() + 1);
            }
            input = tokenize(render(roles, contents));
        }
        if (input.empty() || input.size() + token_limit + 8 > static_cast<size_t>(context_tokens))
            throw std::runtime_error("Mensagem demasiado longa para o contexto. Encurta-a ou aumenta o limite.");
        auto params = llama_context_default_params();
        params.n_ctx = context_tokens;
        params.n_batch = 128;
        params.n_ubatch = 128;
        params.n_threads = threads;
        params.n_threads_batch = threads;
        ctx = llama_init_from_model(model, params);
        if (!ctx) throw std::runtime_error("Sem memoria para o contexto. Reduz o contexto nas definicoes.");
        auto sp = llama_sampler_chain_default_params();
        sampler = llama_sampler_chain_init(sp);
        llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.65f));
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    } catch (const std::exception &e) { end_generation(); fail(env, e.what()); }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_pt_atlas_personal_ai_LocalEngine_nativeNext(JNIEnv *env, jobject) {
    try {
        if (!ctx || stopped.load() || generated >= token_limit) { end_generation(); return nullptr; }
        // Prefill at most 128 tokens per call so cancellation is checked between batches.
        if (position < static_cast<int>(input.size())) {
            const int n = std::min(128, static_cast<int>(input.size()) - position);
            auto batch = llama_batch_get_one(input.data() + position, n);
            if (llama_decode(ctx, batch) != 0) throw std::runtime_error("Falha na inferencia: contexto ou memoria insuficiente.");
            position += n;
            if (position < static_cast<int>(input.size())) return env->NewByteArray(0);
        }
        llama_token token = llama_sampler_sample(sampler, ctx, -1);
        if (llama_vocab_is_eog(vocab, token)) { end_generation(); return nullptr; }
        std::vector<char> piece(256);
        int n = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
        if (n < 0) { piece.resize(-n); n = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false); }
        if (n < 0) throw std::runtime_error("Token invalido.");
        auto batch = llama_batch_get_one(&token, 1);
        if (llama_decode(ctx, batch) != 0) throw std::runtime_error("Falha na geracao. Reduz o contexto.");
        generated++;
        auto bytes = env->NewByteArray(n);
        env->SetByteArrayRegion(bytes, 0, n, reinterpret_cast<const jbyte *>(piece.data()));
        return bytes;
    } catch (const std::exception &e) { end_generation(); fail(env, e.what()); return nullptr; }
}

extern "C" JNIEXPORT void JNICALL
Java_pt_atlas_personal_ai_LocalEngine_nativeStop(JNIEnv *, jobject) { stopped.store(true); }

extern "C" JNIEXPORT void JNICALL
Java_pt_atlas_personal_ai_LocalEngine_nativeResetStop(JNIEnv *, jobject) { stopped.store(false); }

extern "C" JNIEXPORT void JNICALL
Java_pt_atlas_personal_ai_LocalEngine_nativeEnd(JNIEnv *, jobject) { end_generation(); }
