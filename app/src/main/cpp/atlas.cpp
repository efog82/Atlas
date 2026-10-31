#include <jni.h>
#include <llama.h>
#include <atomic>
#include <vector>
#include <string>
#include <stdexcept>
#include <algorithm>
#include <codecvt>
#include <locale>
#include <mutex>
#include <chrono>
#include "cache_policy.h"
#include "chat_runtime.h"
#include <json.hpp>

// One owner (LocalEngine's mutex). Only nativeStop may run concurrently.
namespace {
using Clock = std::chrono::steady_clock;
llama_model *model = nullptr;
llama_context *ctx = nullptr;
llama_sampler *sampler = nullptr;
std::unique_ptr<atlas::ChatRenderer> renderer;
const llama_vocab *vocab = nullptr;
std::once_flag backend_once;
std::atomic<bool> stopped{false};
std::atomic<int64_t> stop_ns{0};
std::vector<llama_token> prompt, cached;
std::string session;
atlas::ContextShape shape;
int position = 0, generated = 0, token_limit = 0, threads = 0, batch_threads = 0;
int phase = 0, evaluated = 0, reused = 0, context_creations = 0;
double prefill_ms = 0, decode_ms = 0, ttft_ms = -1, total_ms = 0, stop_ms = -1, setup_ms = 0;
Clock::time_point start;
int64_t now_ns() { return std::chrono::duration_cast<std::chrono::nanoseconds>(Clock::now().time_since_epoch()).count(); }
double millis(Clock::time_point a) { return std::chrono::duration<double, std::milli>(Clock::now() - a).count(); }
bool abort_compute(void *) { return stopped.load(std::memory_order_relaxed); }

std::string utf8(JNIEnv *env, jstring value) {
    if (!value) return {};
    const jchar *chars = env->GetStringChars(value, nullptr);
    if (!chars) throw std::runtime_error("Sem memoria para o texto.");
    std::u16string text(reinterpret_cast<const char16_t *>(chars), env->GetStringLength(value));
    env->ReleaseStringChars(value, chars);
    return std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t>().to_bytes(text);
}
void fail(JNIEnv *env, const std::string &s) { if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), s.c_str()); }
void clear_cache() { if (ctx) llama_memory_clear(llama_get_memory(ctx), true); cached.clear(); }
void free_sampler() { if (sampler) { llama_sampler_free(sampler); sampler = nullptr; } }
void release_context() { free_sampler(); if (ctx) llama_free(ctx); ctx = nullptr; cached.clear(); session.clear(); shape = {}; }
void finish_turn(bool cancelled) {
    if (cancelled) {
        // An aborted graph may have modified part of a microbatch. Never reuse it.
        clear_cache(); phase = 4;
        const auto requested = stop_ns.load();
        if (requested > 0) stop_ms = (now_ns() - requested) / 1e6;
    } else if (phase != 5) phase = 3;
    total_ms = millis(start);
    free_sampler(); // Keep context + verified KV resident, even after Stop.
}
std::vector<llama_token> tokenize(const std::string &text) {
    int n = llama_tokenize(vocab, text.data(), text.size(), nullptr, 0, true, true);
    if (n >= 0) return {};
    std::vector<llama_token> tokens(-n);
    n = llama_tokenize(vocab, text.data(), text.size(), tokens.data(), tokens.size(), true, true);
    if (n < 0) throw std::runtime_error("Falha ao tokenizar o contexto.");
    tokens.resize(n); return tokens;
}
// Estimate FP16 KV from GGUF metadata (GQA aware). Graph/allocator overhead is estimated separately.
int64_t metadata_number(const std::string &key, int64_t fallback) {
    char buf[128];
    if (llama_model_meta_val_str(model, key.c_str(), buf, sizeof(buf)) < 0) return fallback;
    try { return std::max<int64_t>(1, std::stoll(buf)); } catch (...) { return fallback; }
}
double estimated_memory() {
    if (!model) return 0;
    char arch[128];
    std::string prefix = llama_model_meta_val_str(model, "general.architecture", arch, sizeof(arch)) > 0 ? arch : "unknown";
    const auto embd = metadata_number(prefix + ".embedding_length", 2048);
    const auto layers = metadata_number(prefix + ".block_count", 32);
    const auto heads = metadata_number(prefix + ".attention.head_count", 16);
    const auto kv_heads = metadata_number(prefix + ".attention.head_count_kv", heads);
    const auto dk = metadata_number(prefix + ".attention.key_length", embd / heads);
    const auto dv = metadata_number(prefix + ".attention.value_length", embd / heads);
    const auto kv = layers * kv_heads * (dk + dv) * 2 * (ctx ? llama_n_ctx(ctx) : 0);
    return static_cast<double>(llama_model_size(model) + kv + 192LL * 1048576);
}
// Track only fully successful llama_decode calls. Aborts invalidate all KV, not the allocation.
bool decode(llama_token *tokens, int n, bool prefill) {
    const auto t = Clock::now();
    auto batch = llama_batch_get_one(tokens, n);
    const int result = llama_decode(ctx, batch);
    const double elapsed = millis(t);
    if (prefill) prefill_ms += elapsed; else decode_ms += elapsed;
    if (stopped.load()) { finish_turn(true); return false; }
    if (result != 0) { clear_cache(); throw std::runtime_error("Falha na inferencia. Reduz contexto/lote ou liberta memoria."); }
    cached.insert(cached.end(), tokens, tokens + n);
    return true;
}
}

extern "C" JNIEXPORT void JNICALL Java_pt_atlas_personal_ai_LocalEngine_nativeLoad(JNIEnv *env, jobject, jstring path, jboolean allow_chatml) {
    try {
        release_context(); renderer.reset(); if (model) llama_model_free(model); model = nullptr; vocab = nullptr;
        std::call_once(backend_once, [] { llama_backend_init(); });
        auto params = llama_model_default_params();
        params.n_gpu_layers = 0; params.use_mmap = true; params.use_mlock = false;
        model = llama_model_load_from_file(utf8(env, path).c_str(), params);
        if (!model) throw std::runtime_error("Nao foi possivel carregar o GGUF. Verifica formato e RAM.");
        vocab = llama_model_get_vocab(model); renderer = std::make_unique<atlas::ChatRenderer>(model, allow_chatml); phase = 0;
    } catch (const std::exception &e) { renderer.reset(); if (model) llama_model_free(model); model = nullptr; vocab = nullptr; fail(env, e.what()); }
}
extern "C" JNIEXPORT void JNICALL Java_pt_atlas_personal_ai_LocalEngine_nativeUnload(JNIEnv *, jobject) {
    release_context(); renderer.reset(); if (model) llama_model_free(model); model = nullptr; vocab = nullptr; phase = 0; prompt.clear();
}
extern "C" JNIEXPORT jstring JNICALL Java_pt_atlas_personal_ai_LocalEngine_nativeModelInfo(JNIEnv *env, jobject) {
    if (!model || !renderer) { fail(env, "Modelo indisponivel."); return nullptr; }
    auto meta = [](const char *key) { char value[1024] = {}; llama_model_meta_val_str(model, key, value, sizeof(value)); return std::string(value); };
    nlohmann::ordered_json value = {{"architecture", meta("general.architecture")}, {"name", meta("general.name")},
        {"template", renderer->source}, {"thinkingSwitch", renderer->supports_thinking}, {"reasoning", renderer->reasoning}, {"manualTemplate", renderer->manual}, {"thinkingForced", renderer->render({"user"}, {"test"}, false).thinking_open}};
    return env->NewStringUTF(value.dump().c_str());
}
extern "C" JNIEXPORT jboolean JNICALL Java_pt_atlas_personal_ai_LocalEngine_nativeBegin(JNIEnv *env, jobject,
        jobjectArray jroles, jobjectArray jcontents, jstring session_id, jint context_tokens, jint max_tokens,
        jint n_threads, jint n_threads_batch, jint n_batch, jint n_ubatch, jboolean thinking,
        jfloat temperature, jfloat top_p, jint top_k, jfloat min_p, jfloat repeat_penalty) {
    start = Clock::now(); generated = evaluated = reused = 0; prefill_ms = decode_ms = total_ms = setup_ms = 0; ttft_ms = stop_ms = -1;
    phase = 1; free_sampler(); prompt.clear();
    try {
        if (!model) throw std::runtime_error("Carrega um modelo GGUF primeiro.");
        if (stopped.load()) { finish_turn(true); return false; }
        if (context_tokens < 512 || context_tokens > 4096 || max_tokens < 1 || n_threads < 1 || n_threads > 8 ||
            n_threads_batch < 1 || n_threads_batch > 8 || n_batch < 16 || n_batch > 512 || n_ubatch < 8 || n_ubatch > n_batch)
            throw std::runtime_error("Parametros de inferencia invalidos.");
        token_limit = std::min(static_cast<int>(max_tokens), static_cast<int>(context_tokens) / 2);
        std::vector<std::string> roles, contents;
        const int count = env->GetArrayLength(jroles);
        if (!count || count != env->GetArrayLength(jcontents)) throw std::runtime_error("Conversa invalida.");
        for (int i = 0; i < count; ++i) {
            if (stopped.load()) { finish_turn(true); return false; }
            auto role = static_cast<jstring>(env->GetObjectArrayElement(jroles, i));
            auto content = static_cast<jstring>(env->GetObjectArrayElement(jcontents, i));
            roles.push_back(utf8(env, role)); contents.push_back(utf8(env, content));
            env->DeleteLocalRef(role); env->DeleteLocalRef(content);
        }
        auto rendered = renderer->render(roles, contents, thinking);
        prompt = tokenize(rendered.prompt);
        while (prompt.size() + token_limit + 8 > static_cast<size_t>(context_tokens) && roles.size() > 2) {
            if (stopped.load()) { finish_turn(true); return false; }
            roles.erase(roles.begin() + 1); contents.erase(contents.begin() + 1);
            if (roles.size() > 2 && roles[1] == "assistant") { roles.erase(roles.begin() + 1); contents.erase(contents.begin() + 1); }
            rendered = renderer->render(roles, contents, thinking);
            prompt = tokenize(rendered.prompt);
        }
        if (prompt.empty() || prompt.size() + token_limit + 8 > static_cast<size_t>(context_tokens))
            throw std::runtime_error("Mensagem demasiado longa para o contexto. Encurta-a ou aumenta o limite.");
        const atlas::ContextShape requested{context_tokens, n_batch, n_ubatch};
        if (!ctx || !(shape == requested)) {
            release_context();
            auto params = llama_context_default_params();
            params.n_ctx = context_tokens; params.n_batch = n_batch; params.n_ubatch = n_ubatch;
            params.n_threads = n_threads; params.n_threads_batch = n_threads_batch;
            params.abort_callback = abort_compute; params.abort_callback_data = nullptr;
            params.no_perf = false;
            ctx = llama_init_from_model(model, params);
            if (!ctx) throw std::runtime_error("Sem memoria para o contexto.");
            shape = requested; ++context_creations;
        }
        if (stopped.load()) { finish_turn(true); return false; }
        // Thread-count changes do not require a new context or invalidate KV.
        llama_set_n_threads(ctx, n_threads, n_threads_batch);
        threads = n_threads; batch_threads = n_threads_batch;
        const std::string next_session = utf8(env, session_id);
        size_t prefix = atlas::reusable_prefix(cached, prompt, session == next_session);
        if (!llama_memory_seq_rm(llama_get_memory(ctx), 0, static_cast<llama_pos>(prefix), -1)) { clear_cache(); prefix = 0; }
        cached.resize(prefix); session = next_session; position = reused = static_cast<int>(prefix);
        sampler = atlas::make_sampler({temperature, top_p, top_k, min_p, repeat_penalty});
        for (auto token : prompt) llama_sampler_accept(sampler, token);
        setup_ms = millis(start);
        return rendered.thinking_open;
    } catch (const std::exception &e) { phase = 5; clear_cache(); free_sampler(); total_ms = millis(start); fail(env, e.what()); return false; }
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_pt_atlas_personal_ai_LocalEngine_nativeNext(JNIEnv *env, jobject) {
    try {
        if (!ctx || !sampler || stopped.load() || generated >= token_limit) { finish_turn(stopped.load()); return nullptr; }
        if (position < static_cast<int>(prompt.size())) {
            phase = 1;
            // llama.cpp splits this logical batch into physical microbatches. Abort checks run inside the graph.
            const int n = std::min(shape.batch, static_cast<int>(prompt.size()) - position);
            if (!decode(prompt.data() + position, n, true)) return nullptr;
            position += n; evaluated += n;
            return env->NewByteArray(0);
        }
        phase = 2;
        const auto t = Clock::now();
        llama_token token = llama_sampler_sample(sampler, ctx, -1);
        if (stopped.load()) { finish_turn(true); return nullptr; }
        llama_sampler_accept(sampler, token);
        if (llama_vocab_is_eog(vocab, token)) { finish_turn(false); return nullptr; }
        std::vector<char> piece(256);
        int n = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, true);
        if (n < 0) { piece.resize(-n); n = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, true); }
        if (n < 0) throw std::runtime_error("Token invalido.");
        decode_ms += millis(t);
        if (!decode(&token, 1, false)) return nullptr;
        ++generated;
        if (ttft_ms < 0) ttft_ms = millis(start);
        auto bytes = env->NewByteArray(n);
        if (!bytes) { phase = 5; clear_cache(); free_sampler(); return nullptr; }
        env->SetByteArrayRegion(bytes, 0, n, reinterpret_cast<const jbyte *>(piece.data()));
        return bytes;
    } catch (const std::exception &e) { phase = 5; clear_cache(); free_sampler(); total_ms = millis(start); fail(env, e.what()); return nullptr; }
}
extern "C" JNIEXPORT void JNICALL Java_pt_atlas_personal_ai_LocalEngine_nativeStop(JNIEnv *, jobject) {
    int64_t expected = 0;
    stop_ns.compare_exchange_strong(expected, now_ns()); // Preserve the first request, including the UI click.
    stopped.store(true, std::memory_order_relaxed);
}
extern "C" JNIEXPORT void JNICALL Java_pt_atlas_personal_ai_LocalEngine_nativeResetStop(JNIEnv *, jobject) { stop_ns.store(0); stopped.store(false); }
extern "C" JNIEXPORT void JNICALL Java_pt_atlas_personal_ai_LocalEngine_nativeEnd(JNIEnv *, jobject) {
    if (sampler || phase == 1 || phase == 2) finish_turn(stopped.load());
}
extern "C" JNIEXPORT void JNICALL Java_pt_atlas_personal_ai_LocalEngine_nativeClearCache(JNIEnv *, jobject) { clear_cache(); session.clear(); }
extern "C" JNIEXPORT jdoubleArray JNICALL Java_pt_atlas_personal_ai_LocalEngine_nativeStats(JNIEnv *env, jobject) {
    const double elapsed = (phase == 1 || phase == 2) ? millis(start) : total_ms;
    const double values[] = {static_cast<double>(phase), static_cast<double>(prompt.size()), static_cast<double>(evaluated),
        static_cast<double>(reused), static_cast<double>(generated), prefill_ms, decode_ms, ttft_ms, elapsed,
        static_cast<double>(cached.size()), estimated_memory(), static_cast<double>(context_creations),
        static_cast<double>(shape.context), static_cast<double>(threads), static_cast<double>(batch_threads),
        static_cast<double>(shape.batch), static_cast<double>(shape.microbatch), setup_ms, stop_ms};
    auto out = env->NewDoubleArray(19);
    if (out) env->SetDoubleArrayRegion(out, 0, 19, values);
    return out;
}
