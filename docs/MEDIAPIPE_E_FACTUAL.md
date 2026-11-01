# Atlas 0.5 — MediaPipe e modo factual

O Atlas mantém llama.cpp para GGUF e acrescenta `com.google.mediapipe:tasks-genai:0.10.24` para o **Gemma 3 1B IT .task**. O formato importado escolhe o motor; só um modelo fica carregado. Não se convertem GGUF renomeando ficheiros. O código não contém respostas factuais pré-programadas.

## Instalação

1. Em Mais → Modelos, abre a página do modelo e verifica os termos Gemma. O fornecedor pode exigir aceitação e início de sessão no navegador; a app não guarda credenciais.
2. Usa **Descarregar diretamente .task**, guarda o ficheiro e importa-o com o nome original.
3. Em Definições, define orçamento de pelo menos 2048 MB. O catálogo oficial da Gallery estimava pico de 2 GB para este modelo, não uma medição do Redmi. O carregamento exige também margem na RAM disponível. Um orçamento não impõe um limite ao Android.
4. Carrega o modelo em Modelos. O motor ativo aparece em Definições → Compatibilidade e respostas.
5. Após a instalação, desliga a Internet e experimenta uma conversa. Os pesos não estão no APK.

[Modelo e licença](https://huggingface.co/litert-community/Gemma3-1B-IT) · [ficheiro .task](https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task?download=true).

## Execução

MediaPipe usa **CPU**, sem ativação automática de GPU. A GPU Mali-G76 não foi testada. O modelo pode não carregar num determinado dispositivo; continua disponível a alternativa llama.cpp/GGUF. Não se afirma que MediaPipe seja mais rápido.

O modelo MediaPipe mantém-se carregado entre pedidos; cada pedido cria uma sessão isolada para evitar reutilizar fontes ou conversas incorretas. A KV cache incremental de llama.cpp permanece independente e inalterada. São usados os papéis de Gemma: instruções system no primeiro turno user, assistant como model, BOS fornecido pelo pacote. Os wrappers automáticos são explicitamente desativados para não duplicar o template.

O contexto total é limitado a 2048 tokens e à configuração escolhida ao carregar. Para o alterar, descarrega o modelo da memória e volta a carregar. O histórico mais antigo sai por pares completos. A API MediaPipe não expõe um limite separado de tokens de saída: o controlo de resposta reserva espaço de entrada, mas a geração pode ocupar o contexto restante. Threads, lotes e comparação de perfis de CPU/KV aplicam-se exclusivamente a llama.cpp. Min-p e repetição personalizados não suportados no MediaPipe dão um erro explícito.

A resposta é progressiva. Interromper chama `cancelGenerateResponseAsync`; o código aguarda o fim nativo antes de fechar a sessão para evitar uso de memória já libertada. Não se promete latência de cancelamento: tem de ser medida no aparelho, incluindo o processamento inicial. Métricas de tokens por segundo só ficam disponíveis depois de tokenizar a saída no fim; durante a geração mostram-se tempos. A RAM é uma estimativa, não consumo medido.

## Factualidade

Modo factual mantém instruções para verificar premissas e admitir incertezas, e acrescenta parâmetros conservadores por família:

| Família | Temperatura | Top-p | Top-k |
|---|---:|---:|---:|
| Qwen3 non-thinking | 0,6 | 0,8 | 20 |
| Qwen3 thinking | 0,6 | 0,95 | 20 |
| Gemma 3 | 0,3 | 0,9 | 40 |
| Qwen2.5 | 0,3 | 0,8 | 20 |
| Outros GGUF | 0,3 | 0,9 | 40 |

Qwen3 não usa temperatura zero. As substituições manuais por modelo continuam a ter prioridade; desativa amostragem personalizada para usar o perfil automático. Desligar o modo factual restaura as recomendações anteriores por família. Estes valores são pontos de partida e **não demonstram aumento de exatidão**.

A proteção mais relevante continua a ser Conhecimento: consulta de fontes locais/públicas, excertos identificados, referências e ausência de geração factual sem fontes quando Exigir fontes está ativo. O gerador ainda pode interpretar mal uma fonte. Nem um sampler nem MediaPipe acrescentam conhecimento ao modelo.

## Validação e limites

O workflow compila APK e testes Android, executa testes JVM de formatos/templates/amostragem e ensaios reais de três GGUF em CPU Linux com o perfil factual. Uma sonda separada descarrega o .task real, verifica SHA-256 contra o LFS oficial e inspeciona o pacote; falhas de acesso ficam no relatório e não contam como execução validada. Não executa inferência MediaPipe Android no runner Linux.

Sem um dispositivo ARM64 ligado, não se verificam execução, estabilidade, velocidade, RAM, notificações ou interrupção no Redmi. A verificação em dispositivo deve incluir carregar .task, conversar em modo de avião, cancelar no prefill e na geração, repetir pedidos, mudar para GGUF, consultar agenda/diário e confirmar uma tarefa. Os dados continuam locais; alterações continuam a exigir confirmação.

A primeira tentativa de build identificou que `tasks-genai:0.10.26` não está publicada no Maven Android. Foi substituída por `0.10.24`, a versão fixa no exemplo oficial Gallery correspondente ao modelo `.task`.

## Referências oficiais consultadas

- [Versão Android fixada no exemplo oficial](https://github.com/google-ai-edge/gallery/blob/8ca33ac79e6c7de2a103e59ef7dc9370e302fc57/Android/src/gradle/libs.versions.toml).
- [API LlmInference 0.10.24](https://github.com/google-ai-edge/mediapipe/blob/v0.10.24/mediapipe/tasks/java/com/google/mediapipe/tasks/genai/llminference/LlmInference.java).
- [Sessões, amostragem e cancelamento](https://github.com/google-ai-edge/mediapipe/blob/v0.10.24/mediapipe/tasks/java/com/google/mediapipe/tasks/genai/llminference/LlmInferenceSession.java).
- [PromptTemplates](https://github.com/google-ai-edge/mediapipe/blob/v0.10.24/mediapipe/tasks/java/com/google/mediapipe/tasks/genai/llminference/PromptTemplates.java).
- [Formato oficial de bundles .task](https://github.com/google-ai-edge/mediapipe/blob/v0.10.24/mediapipe/tasks/python/genai/bundler/llm_bundler.py).
- [Catálogo oficial Gallery com Gemma3 .task, CPU e estimativa de RAM](https://github.com/google-ai-edge/gallery/blob/8ca33ac79e6c7de2a103e59ef7dc9370e302fc57/model_allowlist.json).

A API atual do upstream sinaliza migração futura para LiteRT-LM. Esta versão fixa a API MediaPipe pedida, sem misturar `.litertlm` com `.task`.
