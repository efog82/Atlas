# Templates, raciocínio e qualidade — Atlas 0.3.0

## Problemas encontrados

1. **Motor anterior ao suporte completo ao Qwen3.** A app estava fixada em llama.cpp b5046. Passa a b6500, commit `a7a98e0fffed794396b3fbad4dcdbbc184963645`, com suporte às arquiteturas Qwen3 e Gemma 3. As dependências Android mantêm as versões anteriores.
2. **Templates tratados pelo reconhecedor limitado da API C.** `llama_chat_apply_template` não executava integralmente todos os templates Jinja do GGUF nem recebia `enable_thinking`. Um GGUF sem template era silenciosamente tratado como ChatML. Agora a app usa `common_chat_templates_apply` com Jinja, tal como o servidor oficial; valida o template antes de o usar, evitando o fallback silencioso que a própria biblioteca também pode fazer em caso de erro.
3. **Possível duplicação de BOS.** Um template podia escrever `<bos>` e a tokenização acrescentar outro. Agora são passadas as opções `add_bos`/`add_eos` correspondentes ao vocabulário: o formatador remove apenas os tokens de fronteira que a tokenização irá acrescentar. Os testes reais verificam que não existem dois BOS consecutivos.
4. **Uma amostragem para todos.** Antes todos recebiam temperatura 0.65, top-p 0.9 e top-k 40. Agora a família vem de `general.architecture` e `general.name`, não do nome arbitrário do ficheiro; existem valores por família/modo e ajustes persistentes por ficheiro importado.
5. **Pensamento misturado com resposta.** Não havia apresentação própria para Qwen3. Agora `<think>…</think>` e o caso em que o template já abre `<think>` são tratados como estrutura de protocolo; só o texto final é apresentado e persistido. A interface indica que o modelo está a raciocinar. Não é usado o pensamento como resposta quando acaba o orçamento de tokens.
6. **Histórico com papéis misturados e decisões semânticas arbitrárias.** Estados de ações eram enviados como se fossem falas do assistente. Certas perguntas apagavam implicitamente o contexto e respostas vagas eram rejeitadas por listas de frases. Esses mecanismos semânticos foram removidos. Estados reais da aplicação são contexto de sistema; falas continuam `user`/`assistant`. São normalizados papéis consecutivos, removidos fragmentos órfãos e blocos de raciocínio antigos, sem apagar registos Room nem substituir o conteúdo factual gerado.
7. **UTF-8 parcial no streaming.** A conversão direta de um fragmento incompleto podia mostrar temporariamente `�`. O decoder aguarda os bytes completos de acentos/emoji; erros reais de codificação são comunicados.

## Parâmetros automáticos

| Família/modo | Temperatura | Top-p | Top-k | Min-p | Repetição |
| --- | --- | --- | --- | --- | --- |
| Qwen3 non-thinking | 0.7 | 0.8 | 20 | 0 | 1.0 |
| Qwen3 thinking | 0.6 | 0.95 | 20 | 0 | 1.0 |
| Gemma 3 | 1.0 | 0.95 | 64 | 0 | 1.0 |
| Qwen2.5 Instruct | 0.7 | 0.8 | 20 | 0 | 1.05 |
| Outros | 0.7 | 0.9 | 40 | 0 | 1.0 |

São pontos de partida, não uma garantia de qualidade factual. O perfil genérico não presume conhecer as recomendações de todos os autores. Em **Mais → Definições → Compatibilidade e respostas**, a amostragem pode ser personalizada para o modelo carregado e reposta nos valores automáticos. Ajustar temperatura não aumenta o conhecimento dos pesos. Os parâmetros de CPU, cache, memória e interrupção mantêm os controlos anteriores.

Qwen3 híbrido usa `enable_thinking` no template, sem acrescentar comandos ao texto do utilizador. O início é non-thinking para reduzir demora e consumo. Templates exclusivamente thinking/instruct determinam o modo permitido; não é prometida uma alternância que o próprio modelo não suporta. Um template que força pensamento aberto usa os parâmetros do modo thinking, mesmo com a opção global desligada.

A aceitação dos tokens no sampler acompanha prompt e geração, incluindo o estado da penalização de repetição. A cache é comparada com os tokens do novo prompt renderizado; mudar template, modo ou histórico invalida o sufixo incompatível sem recriar o contexto desnecessariamente. A atualização usa a API de memória nova do llama.cpp, mantendo a invalidação segura após interrupção.

## Modo factual

É uma opção de instruções, ativa inicialmente: pedir verificação de premissas, correção de afirmações falsas, distinção entre factos/hipóteses e admissão de incerteza. Não existem respostas factuais pré-programadas, verificação online oculta, traduções automáticas ou um filtro que reescreva a resposta segundo palavras-chave. A validação de ferramentas locais e a confirmação de escritas continuam separadas e obrigatórias.

Modelos pequenos podem continuar a aceitar premissas falsas ou inventar detalhes. Nem o modo factual nem uma resposta bem formatada demonstram exatidão. O Atlas não acede à Internet; uma resposta não pode ser apresentada como pesquisa atualizada.

## Compatibilidade e limites

- Outros GGUF mantêm o seu próprio template, executado por Jinja. Um modelo base sem formato de conversa não é automaticamente convertido num modelo Instruct.
- Sem template, há um erro explícito. Só se o autor do GGUF indicar ChatML deve ser ativada a opção manual **ChatML para GGUF sem template**, seguida de descarregar/carregar o modelo. Um template Jinja inválido continua a produzir erro, sem substituição arbitrária.
- Gemma 3 recebe o sistema/papéis pelo seu template e pelo adaptador oficial, que suporta a incorporação do contexto de sistema na primeira mensagem do utilizador quando necessário. Não se força ChatML nem se inventa um papel `system` incompatível com o formato Gemma.
- Gemma 3 é utilizado para texto. Não foi implementado processamento de imagens/mmproj. Modelos grandes continuam limitados pela RAM do dispositivo.
- Thinking precisa de orçamento suficiente. Se o modelo terminar sem `</think>`/resposta final, aparece um erro útil; não se fabrica uma conclusão. Os limites Android continuam 512–4096 tokens de contexto e até 1024 de resposta. Um modelo que necessite de mais raciocínio pode não completar dentro destes limites.
- O histórico guardado não é eliminado nem reescrito. Para excluir respostas antigas incorretas, usa **Nova conversa** ou elimina a conversa com confirmação.
- Capturas, agenda, diário, tarefas, ferramentas, bloqueio e backups mantêm os fluxos anteriores. As novas opções de inferência são preferências locais; os backups existentes continuam compatíveis e não incluem pesos GGUF.

## Testes automáticos

- `ModelProtocolTest`: valores por família/modo, validação de parâmetros, tokens fragmentados, pensamento aberto pelo template, ausência de resposta final, UTF-8, papéis alternados, conservação do texto gerado e opção factual sem alterar a pergunta.
- `GeneratedModelIntegrationTest`: consome **saídas reais**, verifica templates Qwen3/Gemma, BOS, apresentação final e todos os cortes de bytes no streaming usando as classes Kotlin da app. Sem resultados externos, é explicitamente ignorado, nunca contado como ensaio de inferência.
- `scripts/model-integration/runner.cpp`: compila o **mesmo `chat_runtime.h` da app**, carregando GGUF real e usando o mesmo renderizador/sampler. Não depende de uma CLI configurada de maneira diferente. Testa Qwen3-0.6B Q4_K_M, Gemma-3-1B-IT Q4_K_M e Qwen2.5-0.5B-Instruct Q4_K_M como regressão.
- O workflow verifica SHA-256 contra os metadados LFS e regista repositório, revisão, ficheiro, tamanho e licença. Os pesos não são publicados no artefacto nem incluídos no APK.
- `audit-model-language.py`: classifica idioma da resposta final e de secções em ensaios controlados em português, com `langid==1.1.6`. Não classifica nem censura o raciocínio oculto. Respostas muito curtas ficam assinaladas como não verificadas; nomes próprios, citações e textos técnicos exigem revisão. Um detetor de idioma não é um verificador de verdade.
- Perguntas com premissas falsas são geradas e preservadas para avaliação, sem declarar correção factual a partir da presença de algumas palavras.

Os ensaios de CPU Linux não medem rapidez, RAM, energia, estabilidade do driver ou execução JNI no Redmi. Os testes instrumentados Android precisam de dispositivo ARM64 e de GGUF instalado. Os resultados efetivos e o APK são registados em [VALIDACAO.md](VALIDACAO.md).

## Fontes consultadas

- [llama.cpp b6500 — chat.h](https://github.com/ggml-org/llama.cpp/blob/b6500/common/chat.h), [chat.cpp](https://github.com/ggml-org/llama.cpp/blob/b6500/common/chat.cpp), [minja](https://github.com/ggml-org/llama.cpp/blob/b6500/vendor/minja/chat-template.hpp) e [llama.h](https://github.com/ggml-org/llama.cpp/blob/b6500/include/llama.h).
- [Qwen3 oficial — modos e llama.cpp](https://github.com/QwenLM/Qwen3), [modelo e recomendações](https://huggingface.co/Qwen/Qwen3-0.6B), [template de referência](https://github.com/ggml-org/llama.cpp/blob/b6500/models/templates/Qwen-Qwen3-0.6B.jinja).
- [Implementação oficial Gemma — valores de geração](https://github.com/google/gemma_pytorch/blob/main/gemma/model.py). A leitura da ficha original Gemma no Hugging Face devolveu HTTP 401 nesta sessão; não foram usados tokens nem contornada a autenticação. Os testes usam a conversão GGUF publicamente acessível do ggml-org.
- [Gemma 3 — formato](https://ai.google.dev/gemma/docs/core/prompt-structure), [modelo e configuração](https://huggingface.co/google/gemma-3-1b-it), [termos Gemma](https://ai.google.dev/gemma/terms).
- [Qwen2.5 Instruct](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct).
