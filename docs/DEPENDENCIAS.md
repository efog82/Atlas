# Dependências e referências oficiais

## Estado atual (0.5)

- O build usa llama.cpp **b6500**, commit `a7a98e0fffed794396b3fbad4dcdbbc184963645`, e `common_chat_templates_apply`/Jinja; a API e os ensaios reais estão descritos em [compatibilidade](COMPATIBILIDADE_GGUF.md).
- MediaPipe Android: **tasks-genai 0.10.24**, versão encontrada no catálogo de dependências do exemplo oficial Google Gallery. Foram lidos o código oficial de `LlmInference`, `LlmInferenceSession`, `PromptTemplates` e o criador de bundles. [Referências, correção da versão não publicada e limites](MEDIAPIPE_E_FACTUAL.md).
- As bibliotecas Android/Room/Compose da tabela foram mantidas; a compilação efetiva, lint e testes JVM passaram. Isso não significa que todas as páginas web citadas na primeira tentativa tenham sido consultadas.
- O modelo `.task` e os GGUF são pesos separados, sujeitos às respetivas licenças. Não estão incluídos no APK. A execução MediaPipe em Android/Redmi não foi validada no runner Linux.

## Registo da primeira versão (histórico)

As versões estão fixadas para a combinação AGP 8.9.2 / Gradle 8.11.1 / JDK 17. A tentativa de consultar as fontes e obter os componentes falhou por indisponibilidade do proxy. **A verificação documental exigida pelo pedido não ficou concluída.** Os links abaixo são referências para revisão, não evidência de páginas consultadas nesta sessão.

| Componente | Versão | Fonte oficial |
|---|---|---|
| Android Gradle Plugin | 8.9.2 | https://developer.android.com/build/releases/past-releases/agp-8-9-0-release-notes |
| Gradle | 8.11.1 | https://docs.gradle.org/8.11.1/release-notes.html |
| Kotlin / plugin Compose | 2.1.20 | https://kotlinlang.org/docs/whatsnew2120.html |
| KSP | 2.1.20-1.0.32 | https://github.com/google/ksp/releases |
| Compose BOM | 2025.04.01 | https://developer.android.com/develop/ui/compose/bom/bom-mapping |
| Room | 2.7.1 | https://developer.android.com/jetpack/androidx/releases/room |
| WorkManager | 2.10.1 | https://developer.android.com/jetpack/androidx/releases/work |
| Activity | 1.10.1 | https://developer.android.com/jetpack/androidx/releases/activity |
| Lifecycle | 2.8.7 | https://developer.android.com/jetpack/androidx/releases/lifecycle |
| Fragment | 1.8.6 | https://developer.android.com/jetpack/androidx/releases/fragment |
| Biometric | 1.1.0 | https://developer.android.com/jetpack/androidx/releases/biometric |
| Coroutines | 1.10.2 | https://github.com/Kotlin/kotlinx.coroutines/releases |
| PDFBox Android | 2.0.27.0 | https://github.com/TomRoush/PdfBox-Android |
| llama.cpp | tag b5046 | https://github.com/ggml-org/llama.cpp/tree/b5046 |
| Exemplo Android de referência | mesma versão | https://github.com/ggml-org/llama.cpp/tree/b5046/examples/llama.android |
| Cabeçalho da API C | mesma versão | https://github.com/ggml-org/llama.cpp/blob/b5046/include/llama.h |
| Modelo recomendado | Qwen2.5-0.5B-Instruct-GGUF, Q4_K_M | https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF |

A ponte JNI foi escrita para `llama_model_load_from_file`, `llama_model_get_vocab`, `llama_init_from_model`, `llama_chat_apply_template`, `llama_decode` e sampler chain. Segue o padrão de modelo/contexto nativo e geração fora da UI do exemplo Android; não é uma cópia apresentada como validada do exemplo. O primeiro build deve confirmar a API contra a tag obtida. Não substituas silenciosamente por `master`, porque a API C evolui.

O script regista o SHA Git efetivo para rastreio. Após a publicação no GitHub, a tag foi consultada e o commit `74d4f5b041ad837153b0e90fc864b8290e01d8d5` ficou também fixado e verificado no script.

Português e licença Apache 2.0 são características conhecidas da família Qwen selecionada. A ficha atual, hash/tamanho do GGUF e desempenho neste motor/dispositivo não puderam ser verificados aqui. A app permite importar alternativas suportadas pela versão do motor.

## Verificações posteriores à publicação

Foi possível consultar via ligação GitHub o cabeçalho oficial `include/llama.h` e `examples/llama.android/llama/src/main/cpp/llama-android.cpp` em `b5046`. As funções usadas na ponte JNI estão presentes. A compilação efetiva C++/Kotlin/Gradle terminou com sucesso na execução `41190383308`.

O wrapper oficial foi obtido de `gradle/gradle`, versão `v8.11.1`, incluindo o JAR binário, e incluído no repositório. As limitações de bootstrap descritas na primeira tentativa deixaram de se aplicar. A consulta de todas as restantes páginas de documentação e a validação do modelo em hardware continuam por concluir.
