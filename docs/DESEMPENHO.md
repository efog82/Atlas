# Atlas Personal 0.2 — desempenho offline

## Diagnóstico do código 0.1

1. `nativeBegin` alocava um contexto por resposta e `nativeEnd` libertava-o: não existia KV reutilizável.
2. O histórico completo era tokenizado e novamente passado a `llama_decode` em cada pedido.
3. O system prompt continha `ZonedDateTime.now()` com segundos/nanos: mesmo uma futura cache por prefixo falharia perto do início. Agora só data/fuso ficam no prefixo estável. Uma mudança de dia/fuso invalida-o corretamente.
4. `nativeStop` só alterava um booleano consultado entre chamadas a `llama_decode`; não conseguia interromper um grafo em execução. O callback de abort da API C não estava configurado.
5. Geração e processamento usavam a mesma contagem automática de threads; batch e ubatch estavam fixos em 128.
6. O APK debug não impunha otimização dos kernels nativos. Agora os alvos JNI, llama e ggml CPU recebem `-O3`/`NDEBUG` mesmo no debug. O CI verifica o último `-O` nos comandos reais do compilador. Sem medições do binário anterior no Redmi não é possível quantificar o impacto de cada causa.
7. A UI tentava animar o scroll a cada atualização de texto. Atualizações estão agora limitadas e o scroll automático é espaçado.

## Alterações

- Um modelo e um contexto mantidos em RAM, com acesso serializado pelo mutex do motor. Sampler renovado por pedido, sem destruir o contexto.
- Reutilização do prefixo de **tokens idênticos**, associada à conversa. Sufixo divergente removido com `llama_kv_self_seq_rm`; sempre reavalia o último token do prompt para recuperar logits. Nunca confunde KV com logits válidos.
- Prompt ainda é renderizado/tokenizado para comparar a identidade. Evita-se o trabalho pesado de *decode* do prefixo, não se afirma eliminar a tokenização.
- Mudança de conversa, modelo, edição de texto, truncagem e ferramentas são comparadas pelo conteúdo real. Se a biblioteca não suporta remoção parcial (por exemplo outra arquitetura), a cache é limpa. Não se reutiliza conteúdo incompatível.
- Alterar só threads usa `llama_set_n_threads`, preservando alocação/cache. Alterar contexto, batch ou ubatch recria o contexto no pedido seguinte. Descarregar/eliminar modelo liberta-o.
- Stop imediato por flag atómica, callback dentro do grafo CPU e microlotes menores. Cancelamento por botão, Activity/background ou timeout sinaliza o nativo. Não há reset posterior que possa apagar um Stop concorrente.
- Após abort, KV potencialmente parcial é limpa; o contexto continua alocado. A resposta seguinte volta a calcular o prefixo com segurança. Não há promessa de latência fixa: abort é cooperativo e depende do controlador/kernel/CPU.
- Último modelo carregado guardado nas preferências; auto-carregamento configurável. Na migração, se só existir um GGUF e ainda não existir registo do último, esse ficheiro é escolhido. Falhas de RAM/carga dão erro sem ciclo de tentativas.

## Perfis iniciais (não são resultados de benchmark)

| Perfil | n_threads | n_threads_batch | n_batch | n_ubatch | Contexto | Saída máxima |
|---|---:|---:|---:|---:|---:|---:|
| Equilibrado | 2 | 4 | 128 | 32 | 2048 | 192 |
| Rápido | 4 | 4 | 256 | 64 | 2048 | 256 |
| Económico | 2 | 2 | 64 | 16 | 1024 | 128 |

Em **Mais → Definições → Desempenho offline**, escolhe perfil ou ajusta threads/lotes. Threads são limitadas aos núcleos disponíveis, sem afinidade forçada nem instruções ISA não verificadas no Redmi. Um batch lógico pode conter vários microlotes físicos; o callback consegue abortar durante esse processamento. Mais threads não garantem maior velocidade, principalmente num SoC heterogéneo sob carga/temperatura.

O perfil Económico reduz paralelismo, tamanho do contexto e comprimento de resposta; **não é uma medição de energia**. Alterações existentes de contexto/saída são preservadas na atualização até escolher um perfil. O primeiro pedido depois da carga ou limpeza de cache continua a ter um processamento inicial completo.

## Métricas reais por pedido

A conversa e as definições mostram: preparação de contexto/tokenização, processamento inicial (ms e tokens/s), tokens realmente avaliados/reutilizados, geração (ms e tokens/s), tempo até primeiro token, total, latência de Stop quando disponível, células KV e contagem de contextos criados. Usa `steady_clock` nativo; carregamento usa relógio monotónico Android. A velocidade de geração mede sampling/decode dos tokens emitidos e é separada da velocidade de processamento inicial. É por chamada do motor; uma resposta de agente com várias ferramentas pode ter várias chamadas.

RAM estimada = bytes dos tensores do modelo + KV FP16 dimensionada por camadas/cabeças KV/dimensões do GGUF + margem de 192 MB para grafo/alocador. Considera GQA; não é RSS, PSS, pico real nem consumo total do Android. A verificação preventiva de admissão continua conservadora. Não recolhe telemetria nem transmite métricas.

## Comparar no próprio Redmi

Botão **Comparar perfis neste dispositivo**, com confirmação: três perfis, dois pedidos por perfil (primeiro sem cache, depois continuação), prompt sintético igual, contexto 1024 e limite 64 para todos. Não usa diário/memória; limpa a KV, mantendo histórico e modelo. Pode ser interrompido. Limite de cinco minutos por pedido; interrompe antes do perfil seguinte se o estado térmico Android for severo.

A interface mostra resultados medidos e permite exportar JSON com parâmetros efetivos, tokens, tempos, modelo, Android, SoC e estado térmico. EOS pode fazer variar o comprimento: regista o número efetivo, não inventa 64 tokens. Variações de texto, ordem dos perfis, cache de ficheiros, bateria, outras apps e throttling influenciam o resultado. Não é uma medição rigorosa de joules, temperatura em graus ou energia por token.

Para uma comparação fiável, fazer três passagens com arrefecimento, repetir alterando ordem manualmente (selecionar perfis e repetir o mesmo prompt), modo avião, mesmas definições de brilho/bateria e sem carregador. Comparar mediana de TTFT, prompt tok/s, geração tok/s e estabilidade térmica. Verificar Stop durante prompt longo e durante geração, e que o pedido seguinte funciona. Confirmar que os contextos criados não aumentam em cada mensagem normal e que a segunda mensagem tem `reutilizados > 0`.

## Vulkan e Mali-G76 MC4

A [ficha Xiaomi do Redmi Note 10S](https://www.mi.com/uk/product/redmi-note-10s/specs/) confirma Helio G95 e Mali-G76 MC4. A designação da GPU não demonstra as capacidades do driver no firmware Android 13 instalado.

No [backend Vulkan da revisão usada](https://github.com/ggml-org/llama.cpp/blob/74d4f5b041ad837153b0e90fc864b8290e01d8d5/ggml/src/ggml-vulkan/ggml-vulkan.cpp), a inicialização exige Vulkan 1.2 e armazenamento de 16 bits; a utilização de FP16 também depende de features/extensões do dispositivo. Estes requisitos foram consultados no código oficial. Não se pode concluir que o driver Mali deste telemóvel os satisfaz sem o consultar. O [callback de abort desta versão](https://github.com/ggml-org/llama.cpp/blob/74d4f5b041ad837153b0e90fc864b8290e01d8d5/include/llama.h) documenta suporte apenas CPU.

**Este APK força `GGML_VULKAN=OFF` e `n_gpu_layers=0`.** Não existe seleção escondida de GPU. Antes de uma variante experimental, recolher `vulkaninfo`/capacidades de dispositivo (Vulkan API, storageBuffer16BitAccess, shaderFloat16, extensões e limites), testar shaders Q4_K_M, comparar saídas com CPU e executar carga contínua, Stop, cancelamento, reentrada e pressão de RAM. Só considerar ativação se estável e com benefício medido. A CPU permanece a alternativa segura. Não foram executados testes Vulkan ou medições de GPU nesta sessão.

## Testes

- Host C++: planeamento de prefixo exato, reavaliação de logits, edições, isolamento e forma do contexto (`scripts/test-native-policy.sh`).
- JVM: parâmetros, limites de núcleos, perfis, taxas calculadas de tempos medidos e estabilidade do prefixo de instruções.
- Android com GGUF instalado: continuação com KV, troca de conversa, preservação de contexto ao alterar threads, Stop durante prefill/geração, timeout e pedido posterior. Ignorados sem GGUF; não executados pelo runner x86.
- CI: compila APK normal e APK dos testes Android, executa testes JVM/lint/host, verifica `-O3` dos comandos reais e publica artefactos. Isto não é um benchmark ARM64.

Não há números de velocidade do Redmi medidos nesta sessão. A confirmação de ganho, tempo de Stop, RAM real e energia tem de ser feita no telemóvel.
