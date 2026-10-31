# Validação — 31 de outubro de 2026

## Versão 0.2.1 — conversa, notas e cancelamento (mais recente)

- Commit compilado: `c9d80a84dbb65974354d1e7feee515b783ae0aab`.
- [Execução 41219095049](https://github.com/efog82/Atlas/actions/runs/41219095049): **success**.
- `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`: **BUILD SUCCESSFUL**.
- 41 testes JVM, teste nativo de cache, sete verificações estáticas e verificação de `-O3` aprovados.
- 13 testes Android compilados, incluindo persistência do cancelamento e nota sem tarefa/evento; não executados no runner x86.
- [APK 0.2.1 e relatórios](https://github.com/efog82/Atlas/actions/runs/41219095049/artifacts/13049640856), ZIP de 26 801 967 bytes.
- SHA-256 comunicado pelo GitHub: `8a7f4592b6433c4b388736595df86b10ea18c91aa86dd35c09ab307796e3166d`.
- [Correção e percurso de regressão no Redmi](CORRECAO_CONVERSA.md). A geração com Qwen no dispositivo não foi executada nesta sessão.

## Versão 0.2 — otimização offline (anterior)

- Código compilado: `7c0518b370b8b9a7af0458b2c2296ea943528ba7`.
- [Execução GitHub Actions 41207326535](https://github.com/efog82/Atlas/actions/runs/41207326535): **success**.
- `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`: **BUILD SUCCESSFUL**.
- 23 testes JVM executados; teste host C++ da política de cache e sete verificações estáticas aprovados.
- `verify-native-optimization.py` confirmou `-O3` efetivo nos comandos do compilador da ponte JNI e dos kernels CPU, incluindo no APK debug.
- APK normal e APK de testes Android compilados. Os 11 testes instrumentados, incluindo três de KV/interrupção/timeout com GGUF, **não foram executados** no runner x86.
- [APK 0.2 e relatórios (ZIP)](https://github.com/efog82/Atlas/actions/runs/41207326535/artifacts/13045560637), 26 788 374 bytes.
- SHA-256 do ZIP comunicado pelo GitHub: `32ed5b028787a7da2577db549a9bbe0df95c5647fc5bb789634eca045a5dc168`.
- Sem medições de TTFT, tokens/s, RAM real, energia ou latência de interrupção no Redmi Note 10S. Há perfis, métricas e comparação local no próprio dispositivo para obter essa evidência.
- Vulkan continua desativado; requisitos revistos no código oficial, estabilidade do driver Mali não ensaiada.
- [Diagnóstico, configurações e percurso de teste](DESEMPENHO.md).

Os registos seguintes são históricos das versões anteriores.

## Atualização anterior: compilação concluída no GitHub Actions

- Repositório publicado: https://github.com/efog82/Atlas
- Commit compilado: `1a4208f2b391304641b5192175c89a33802d3786`.
- Execução: https://github.com/efog82/Atlas/actions/runs/41190383308 — **success**.
- `testDebugUnitTest lintDebug assembleDebug`: **BUILD SUCCESSFUL**, incluindo compilação Kotlin, Room/KSP e JNI/C++ ARM64. Testes JVM executados sem falhas; lint aprovado.
- Sete verificações estáticas também executadas com sucesso no runner.
- Artefacto `atlas-personal-debug-arm64`, ID `13038955824`, ZIP de 27 224 231 bytes com APK e relatórios. [Descarregar](https://github.com/efog82/Atlas/actions/runs/41190383308/artifacts/13038955824).
- SHA-256 do ZIP, comunicado pelo GitHub: `bc3b2d370ebf87f28a6300e977f55e8d5095cff0ad96f3577a8862b262666617`.
- llama.cpp: `b5046`, commit `74d4f5b041ad837153b0e90fc864b8290e01d8d5`, confirmado e fixado no script.
- Wrapper oficial Gradle incluído e validado contra o blob da versão `v8.11.1`: SHA-256 `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`.
- Corrigidos durante a compilação: pacote Android obsoleto `tools` no workflow e retorno antecipado numa função Kotlin com corpo de expressão no ReminderWorker.

**Não executado:** instalação/abertura em dispositivo Android, testes instrumentados, inferência real com modelo, validação de persistência no dispositivo, entrega de notificações e medições de RAM/velocidade. A compilação bem-sucedida não demonstra esses critérios.

O restante relatório abaixo documenta a primeira tentativa local, anterior à publicação. Os estados de build e testes JVM dessa tentativa foram superados pela execução acima; os limites funcionais e a necessidade de aceitação em dispositivo continuam aplicáveis.

---

## Executado neste ambiente

- `python3 scripts/check-project.py`: **7 verificações estáticas aprovadas**: XML, permissões no manifesto fonte, símbolos JNI, ficheiros/testes, configuração Android/ABI, entrada de confirmação e workflow.
- `bash -n gradlew scripts/setup-native.sh scripts/bootstrap-gradle.sh`: sintaxe shell aprovada.
- Ambiente: Java runtime 21 presente; SDK/NDK Android, CMake, Gradle, compilador Kotlin, dispositivo Android e modelo GGUF não encontrados.
- `./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug`: **não iniciou a compilação**. Bootstrap falhou com `curl: (7) Failed to connect to proxy port 8080`. Exit code 7.
- `bash scripts/setup-native.sh`: **falhou** ao obter a biblioteca oficial com o mesmo erro de proxy. Exit code 128 do Git.
- Não há remote GitHub configurado nem workflow executado.

Estas verificações não demonstram compilação Kotlin/C++, correção de Room em Android, renderização Compose, entrega de notificações ou inferência. Não substituem os testes funcionais.

## Testes escritos, não executados

**17 testes JVM:** interpretação de lembretes, rejeição de chamadas inválidas/ambiguidades, autorização de memória, datas passadas/mudança de hora, recorrências, pesquisa literal, backup round-trip, aleatoriedade, integridade e palavra-passe errada.

**8 testes Android:** persistência ao reabrir Room; confirmação e operações de tarefas; cascatas; pesquisa literal; backup/restore e falha sem alteração; abertura; ausência da permissão de Internet; geração real com GGUF instalado. Alguns testes agregam várias operações.

`LocalInferenceTest` fica ignorado se não houver modelo. Um resultado ignorado não satisfaz o critério de inferência funcional.

## Critérios obrigatórios

| Critério | Código | Evidência nesta sessão |
|---|---|---|
| Abre em Android 13 | API 33+, Activity e Compose | Não executado |
| Agenda, diário e tarefas funcionais | CRUD, ecrãs, recorrências/notificações | Não executado |
| Base persiste registos | Room e transações | Não executado em Android |
| Respostas reais por GGUF local | JNI, tokenização, decode e sampling | Não compilado nem executado |
| Assistente consulta dados | Ferramentas e resultados locais citados | Não executado |
| Cria tarefa após confirmação | Proposta, validação e confirmação | Não executado |
| Offline verificável | Sem INTERNET no manifesto fonte; teste escrito | Fonte verificada, APK/dispositivo não verificados |
| Compilável no Android Studio | Gradle/CMake e preparação | Build bloqueado antes de começar |

**A entrega não está validada como concluída pelos critérios obrigatórios.** É necessário compilar, corrigir eventuais incompatibilidades detetadas pelo compilador e executar testes e percurso abaixo em hardware adequado.

## Percurso de aceitação num Android ARM64

1. Preparar dependências e compilar conforme README. Guardar revisão llama.cpp e relatórios.
2. Instalar num Android 13 ARM64. Criar tarefa, evento, diário, memória autorizada e apontamento. Fechar à força/reabrir e confirmar persistência.
3. Editar/concluir tarefa, cancelar e confirmar eliminações. Criar eventos diários/semanais/mensais e verificar as três vistas, mudança de hora e fim do mês.
4. Autorizar notificações/alarmes exatos. Agendar um lembrete para minutos depois. Editar/apagar outro e confirmar ausência de notificação antiga. Repetir após reinício e com WorkManager, admitindo atrasos Android.
5. Importar GGUF, conferir tamanho/SHA-256 com a origem, carregar e medir RAM/tempo até primeiro token. Ativar modo de avião e gerar texto novo. Interromper, enviar outra mensagem, descarregar e recarregar. Confirmar erro recuperável para ficheiro inválido.
6. Pedir `Quais são as minhas tarefas?`. Comparar os dados recuperados com Room e distinguir fontes de texto gerado. Pesquisar no diário e nos documentos.
7. Pedir `Atlas, lembra-me de estudar amanhã às 20h.`. Antes da confirmação não deve existir tarefa. Cancelar e verificar ausência. Repetir, confirmar e verificar tarefa/lembrete reais.
8. Importar TXT/PDF, pesquisar frase conhecida, abrir página/secção, pedir resumo e iniciar revisão. Conferir referências e respostas do modelo.
9. Exportar backup, alterar dados e tentar restaurar com palavra-passe errada: dados não mudam. Restaurar corretamente e comparar. Confirmar que o diário não virou memória permanente.
10. Ativar bloqueio, sair/reentrar e desbloquear. Repetir com temas e rotação. Confirmar UI responsiva durante inferência e comportamento com pouca RAM.
11. Com modelo importado e bloqueio desligado, executar `./gradlew connectedDebugAndroidTest`. Registar testes executados/ignorados e Android/SoC/RAM.

## Limitações atuais

- Nenhum hardware foi medido. As estimativas de RAM não são resultados de testes.
- CPU ARM64; sem GPU/NPU, OCR, calendários de outras apps ou sincronização externa.
- Edição/eliminação de recorrências por série, sem exceções individuais ou RRULE arbitrário. Eventos de vários dias não são repartidos em cada célula do calendário.
- Importação até 20 MB, PDF até 300 páginas/4 milhões de caracteres, TXT até 4 MB. Backup até 64 MB serializados; GGUF até 4 GB e sujeito à RAM.
- Contexto antigo removido por mensagens completas. Sem resumo automático da conversa. Uma mensagem ou resultado isolado que exceda o contexto é rejeitado com erro.
- Modelos pequenos podem falhar português, ferramentas e avaliação de estudo. O interpretador determinístico cobre comandos explícitos e o padrão limitado de lembrete documentado.
- Propostas pendentes não sobrevivem à morte do processo; é necessário refazer e confirmar. Pedidos/respostas concluídas persistem. A geração para ao sair da Activity; não há geração em serviço de background.
- Notificações exigem permissão; WorkManager pode atrasar. Lembretes únicos que passaram com o dispositivo desligado não são recriados retroativamente.
- Room sem SQLCipher; usa isolamento/proteção Android. Biometria bloqueia interface, backups têm encriptação própria.
- APK apenas `arm64-v8a`; emuladores x86 não são alvo de suporte.
