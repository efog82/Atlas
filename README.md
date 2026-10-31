# Atlas Personal

**Versão 0.3.0:** Jinja do GGUF, Qwen3 thinking/non-thinking, amostragem por modelo, modo factual e histórico com papéis normalizados. Integração real Qwen3/Gemma 3/Qwen2.5, sem substituir respostas por factos pré-programados. [Problemas encontrados, alterações e resultados](docs/COMPATIBILIDADE_GGUF.md).

**Versão 0.2.3:** lembretes interpretados localmente com confirmação, agenda/diário com dados reais, contexto de conversa revisto e diagnóstico de capturas. **Qualidade factual do chat ainda limitada** nos ensaios reais Qwen 0.5B/1.5B. [Alterações e resultados completos](docs/OPERACOES_LOCAIS.md).

**Versão 0.2.2:** cabeçalho de data/hora retirado das mensagens de conversa e capturas de ecrã permitidas. [Correção e testes](docs/CORRECAO_HORA.md).

**Versão 0.2.1:** corrigidas propostas indevidas em conversa e repetição de ações canceladas, incluindo conversas antigas. [Diagnóstico e testes](docs/CORRECAO_CONVERSA.md).

**Versão 0.2:** contexto persistente, KV cache por conversa, interrupção dentro do grafo CPU, perfis e comparação local com métricas. [Análise e testes de desempenho](docs/DESEMPENHO.md). Não há resultados de velocidade do Redmi inventados.

Aplicação Android nativa para organização pessoal e conversas com um modelo GGUF local. Kotlin, Compose/Material 3, MVVM, StateFlow, Room, WorkManager, AlarmManager e JNI/llama.cpp. Android 13+ (API 33), exclusivamente ARM64.

**Estado real:** Atlas 0.3.0 debug ARM64 compilado, lint e 65 testes JVM aprovados (um teste de integração externo ignorado nesta tarefa por não receber pesos). A tarefa isolada de integração executou oito testes, incluindo um que valida dez gerações reais com três GGUF; verificações de templates, BOS, contagem do sampler, tokens especiais, UTF-8 e idioma aprovadas. Estes testes não certificam a verdade de todas as respostas. [Execução validada](https://github.com/efog82/Atlas/actions/runs/41237557207) · [Descarregar APK e relatórios (ZIP)](https://github.com/efog82/Atlas/actions/runs/41237557207/artifacts/13056560100).

Extrai o ZIP e instala `app/build/outputs/apk/debug/app-debug.apk`. Os 15 testes instrumentados foram compilados, mas não executados em Android/Redmi. Os modelos não estão no APK. Os ensaios reais foram em CPU Linux; Qwen3 0.6B e Qwen2.5 0.5B ainda erraram factos, enquanto Gemma 3 1B foi mais consistente nestes exemplos. [Respostas e evidência](https://github.com/efog82/Atlas/actions/runs/41237557207/artifacts/13055952715) · [Relatório completo](docs/VALIDACAO.md).

## Funcionalidades no código

- **Início:** resumo diário, compromissos, tarefas abertas e atalhos.
- **Atlas IA:** inferência local via JNI, streaming, interrupção, histórico Room, várias conversas, contexto limitado por tokens e verificações preventivas de memória. Sem respostas simuladas.
- **Agente:** consultas à agenda, tarefas, diário, notas, biblioteca e memórias; JSON com ferramentas permitidas e argumentos validados. Confirmação separada antes de criar tarefas, eventos ou memórias. Sem ferramentas destrutivas.
- **Agenda:** mês/semana/dia, recorrências diárias/semanais/mensais, edição de séries e CRUD de tarefas. Alarmes locais e recuperação após reinício; fallback WorkManager quando não há permissão para alarmes exatos.
- **Diário:** registos datados, categorias, etiquetas e pesquisa. Não é convertido automaticamente em memória.
- **Estudos:** apontamentos por tema, TXT UTF-8/PDF com texto selecionável, pesquisa, referências por página/secção, resumos e revisão pela IA, progresso manual persistente. Citações recuperadas separadas do texto gerado.
- **Mais:** hábitos, memórias autorizadas editáveis/exportáveis, gestão GGUF, temas, limites de contexto/resposta/RAM, bloqueio biométrico/credenciais e backups AES-256-GCM.

A app não declara permissão `INTERNET`, não usa Firebase e não faz pedidos HTTP. O modelo é obtido pelo navegador ou transferido por USB e importado pelo seletor Android. O seletor pode mostrar fornecedores na nuvem: para instalar inteiramente offline, escolhe ficheiros já existentes no dispositivo.

## Compilar

Requer um computador com acesso à rede para preparar as dependências da compilação. Depois de instalado o modelo, a utilização da app não precisa de rede.

1. Instala Android Studio com JDK **17**, SDK **35**, Build Tools **35.0.0**, NDK **27.2.12479018**, CMake **3.22.1**, Git, curl e unzip.
2. Obtém a biblioteca nativa oficial:

   ```bash
   bash scripts/setup-native.sh
   ```

   O script obtém a tag `b6500` de `ggml-org/llama.cpp`, regista o commit efetivo em `native/llama-revision.txt` e copia a licença original para os assets. O código JNI usa tokenização, decode e sampling reais. Se a pasta `native/llama.cpp` ainda contiver b5046, renomeia-a para preservar o clone antigo antes de executar o script; o script recusa misturar revisões.

3. Configura `ANDROID_HOME` ou deixa o Android Studio criar `local.properties` com `sdk.dir`. Não publiques esse ficheiro.
4. Compila e executa os testes JVM:

   ```bash
   ./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
   ```

   O wrapper oficial Gradle 8.11.1 está incluído (`gradlew`, `gradlew.bat` e `gradle/wrapper/gradle-wrapper.jar`), obtido da versão oficial `v8.11.1` do projeto Gradle. No Windows usa `gradlew.bat`. No Android Studio abre a pasta, sincroniza o projeto e usa **Build APK(s)**. Git Bash permite executar `setup-native.sh` no Windows. Os scripts de bootstrap separados são opcionais.

5. Se o build terminar com sucesso, o APK fica em `app/build/outputs/apk/debug/app-debug.apk`.

Versões fixadas: AGP 8.9.2, Kotlin/Compose compiler 2.1.20, KSP 2.1.20-1.0.32, Compose BOM 2025.04.01, Room 2.7.1, WorkManager 2.10.1. [Referências oficiais e estado da verificação](docs/DEPENDENCIAS.md).

## Instalar APK e modelo

1. Instala o APK compilado num Android 13+ ARM64, autorizando essa origem, ou usa:

   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

2. Abre **Mais → Modelos**. A app apresenta as opções e os limites efetivamente observados. Nos exemplos de CPU ensaiados, **Gemma 3 1B IT Q4_K_M** deu respostas mais consistentes. **Qwen3 0.6B Q8_0** passou os testes de formato/thinking, mas ainda errou factos; **Qwen2.5 0.5B Q4_K_M** também mostrou qualidade factual limitada. Isto não é uma medição de qualidade geral nem validação no Redmi.
3. Obtém um dos GGUF pelos links da app: Gemma 3 (806 MB de pesos), Qwen3 oficial (639 MB) ou a opção Qwen2.5 mais pequena (491 MB). Os links Qwen3/Gemma estão fixados nas revisões testadas. Lê a licença: Apache 2.0 para Qwen e termos próprios Gemma. Usa navegador/computador ou USB e importa um único GGUF. Fragmentos `-00001-of-...` devem ser unidos previamente no computador com `llama-gguf-split`.
4. Toca em **Importar GGUF** e depois **Carregar**. A importação calcula SHA-256; compara-o com o fornecedor para verificar a origem. O cabeçalho é validado antes da importação e a arquitetura pelo motor ao carregar. Outros GGUF dependem do suporte na versão fixada do motor e da RAM.
5. Autoriza notificações e, para precisão horária, alarmes exatos em **Mais → Definições**.
6. Ativa modo de avião e envia uma pergunta em **Atlas IA**. Sem modelo carregado, a app pede a instalação e não fabrica respostas.

**Hardware:** não há medições físicas de RAM, rapidez ou energia no Redmi nesta sessão. Os tamanhos acima são apenas dos pesos; contexto, buffers e sistema precisam de memória adicional. A verificação de RAM da app é preventiva, não uma quota do processo. Começa com contexto pequeno e modo non-thinking; o Android pode terminar processos por falta de RAM. GPU/NPU continuam desativadas.

## Ferramentas e estudo

- `Atlas, lembra-me de estudar amanhã às 20h.` prepara uma proposta com data/hora. Só depois de **Confirmar e guardar** existe tarefa e lembrete real. Este interpretador limitado também funciona sem modelo e é identificado como operação da aplicação.
- `Quais são as minhas tarefas?`, `/tarefas`, `/agenda`, `/memorias`, `/notas assunto`, `/diario assunto` e `/estudo assunto` usam consultas locais explícitas. A aplicação apresenta os dados; só gera uma explicação adicional se existir modelo carregado.
- Outros pedidos usam ferramentas JSON validadas. Modelos pequenos podem falhar formato ou interpretação; chamadas inválidas são rejeitadas.
- Em Estudos, pesquisa o TXT/PDF e usa **Resumir excerto** ou **Rever com IA**. São enviadas ao motor até três páginas/secções parciais, para limitar o contexto; não é um resumo integral de um documento longo. A revisão é uma conversa com uma pergunta de cada vez; o histórico persiste. O botão **Estudei +10%** regista progresso manual, não avaliação automática.
- Pesquisa textual, sem embeddings. As avaliações e explicações geradas podem conter erros. As fontes recuperadas não são convertidas em memórias pessoais.

## Privacidade e cópias

Room guarda os dados no diretório privado da app, protegido pelo Android; não usa SQLCipher. Biometria bloqueia a interface e não substitui o bloqueio/encriptação do dispositivo. Capturas de ecrã estão permitidas a pedido do utilizador, para facilitar a partilha de erros. O bloqueio biométrico mantém-se disponível.

**Mais → Cópias:** escolher destino e palavra-passe de pelo menos 10 caracteres. AES-256-GCM, salt/nonce aleatórios, PBKDF2-HMAC-SHA256 com 210 000 iterações e cabeçalho autenticado. A restauração valida o conteúdo antes de substituir os dados numa transação Room, mediante confirmação. Backups incluem registos, conversas e texto importado; não incluem modelos nem definições de bloqueio. Limite: 64 MB serializados. Sem palavra-passe, a cópia é irrecuperável.

Exportar memórias em JSON é uma ação separada **sem encriptação**, com aviso e confirmação. Usa backups para uma exportação protegida. Apagar dados mantém modelos/definições; desinstalar remove o armazenamento privado. A eliminação não promete apagamento físico da flash nem elimina cópias já exportadas.

## Testes e GitHub Actions

```bash
python3 scripts/check-project.py       # verificações estáticas, sem Android
./gradlew testDebugUnitTest            # testes JVM executados com sucesso no GitHub Actions
./gradlew connectedDebugAndroidTest   # 15 testes instrumentados compilados; execução requer Android ARM64 e GGUF
```

O teste de inferência procura um GGUF já importado. Sem ele fica **ignorado**, não aprovado. O teste de abertura pressupõe bloqueio da app desligado. Testes de Room/backup usam bases próprias. [Procedimento e resultados](docs/VALIDACAO.md).

O [workflow Android](.github/workflows/android.yml) instala SDK/NDK, prepara llama.cpp, executa testes JVM/lint, compila e publica APK e relatórios no artefacto `atlas-personal-debug-arm64`. O runner x86 não executa testes instrumentados. A tarefa adicional `model-integration` compila o renderizador/sampler partilhados, descarrega revisões GGUF fixadas, verifica SHA-256, executa gerações reais, valida-as no parser Kotlin da app e audita o idioma. Estes ensaios CPU não substituem testes Android/Redmi. Repositório GitHub: [efog82/Atlas](https://github.com/efog82/Atlas). Consulta [Actions](https://github.com/efog82/Atlas/actions) para acompanhar a compilação. Quando a execução terminar com sucesso, descarrega o artefacto `atlas-personal-debug-arm64`. Uma compilação bem-sucedida não demonstra execução em dispositivo.

## Ficheiros

| Caminho | Conteúdo |
|---|---|
| `app/src/main/java/pt/atlas/personal/ui/` | Seis ecrãs Compose, componentes, tema e ViewModel |
| `data/` | Entidades/DAO Room, repositório e recorrência |
| `ai/` e `app/src/main/cpp/` | Importação GGUF, motor e inferência JNI/llama.cpp |
| `agent/` | Protocolo e ferramentas locais |
| `reminders/` | Alarmes, notificações, WorkManager e reinício |
| `security/` | Preferências, encriptação e backup transacional |
| `study/` | Extração TXT/PDF |
| `app/src/test/` e `app/src/androidTest/` | Testes JVM e instrumentados |
| `scripts/`, Gradle e `.github/workflows/` | Preparação e compilação |

Os caminhos abreviados estão dentro de `app/src/main/java/pt/atlas/personal/`. As dependências são obtidas na preparação; o repositório não inclui pesos GGUF nem llama.cpp vendorizado.
