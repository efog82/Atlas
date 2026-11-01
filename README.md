# Atlas Personal

**Versão 0.5.0:** MediaPipe CPU para Gemma 3 1B `.task`, ligação de descarga, importação, streaming e cancelamento; perfil factual por família no llama.cpp. [Utilização e limites](docs/MEDIAPIPE_E_FACTUAL.md).

**Versão 0.4.0:** procura automática de fontes online quando há Internet, biblioteca offline com excertos citados e consultas pessoais locais. Controlos em **Mais → Conhecimento**. A fonte online inicial é a Wikipédia; não é um serviço de notícias/meteorologia em tempo real nem uma garantia de verdade. [Funcionamento e privacidade](docs/CONHECIMENTO_E_ONLINE.md).

**Versão 0.3.0:** Jinja do GGUF, Qwen3 thinking/non-thinking, amostragem por modelo, modo factual e histórico com papéis normalizados. Integração real Qwen3/Gemma 3/Qwen2.5, sem substituir respostas por factos pré-programados. [Problemas encontrados, alterações e resultados](docs/COMPATIBILIDADE_GGUF.md).

**Versão 0.2.3:** lembretes interpretados localmente com confirmação, agenda/diário com dados reais, contexto de conversa revisto e diagnóstico de capturas. **Qualidade factual do chat ainda limitada** nos ensaios reais Qwen 0.5B/1.5B. [Alterações e resultados completos](docs/OPERACOES_LOCAIS.md).

**Versão 0.2.2:** cabeçalho de data/hora retirado das mensagens de conversa e capturas de ecrã permitidas. [Correção e testes](docs/CORRECAO_HORA.md).

**Versão 0.2.1:** corrigidas propostas indevidas em conversa e repetição de ações canceladas, incluindo conversas antigas. [Diagnóstico e testes](docs/CORRECAO_CONVERSA.md).

**Versão 0.2:** contexto persistente, KV cache por conversa, interrupção dentro do grafo CPU, perfis e comparação local com métricas. [Análise e testes de desempenho](docs/DESEMPENHO.md). Não há resultados de velocidade do Redmi inventados.

Aplicação Android nativa para organização pessoal e conversas com um modelo GGUF ou .task local. Kotlin, Compose/Material 3, MVVM, StateFlow, Room, WorkManager, AlarmManager, JNI/llama.cpp e MediaPipe. Android 13+ (API 33), exclusivamente ARM64.

**Estado real:** Atlas 0.5.0 debug ARM64 compilado, lint e 75 testes JVM aprovados (um teste condicional de resultados externos ignorado na tarefa Android). A integração isolada executou 11 testes, incluindo a validação de 13 gerações reais com três GGUF e o perfil factual. Os testes verificam formatos, templates, sampler e apresentação; não certificam verdade factual. [Execução validada](https://github.com/efog82/Atlas/actions/runs/41326861929) · [Descarregar APK e relatórios (ZIP)](https://github.com/efog82/Atlas/actions/runs/41326861929/artifacts/13091612775).

Extrai o ZIP e instala `app/build/outputs/apk/debug/app-debug.apk`. Os **17 testes instrumentados foram compilados, mas não executados** em Android/Redmi. Os modelos não estão no APK. A integração MediaPipe está compilada, mas não foi executada num telemóvel nesta sessão. O `.task` oficial exige sessão/termos no Hugging Face (a sonda pública recebeu HTTP 401). [Respostas GGUF e relatórios](https://github.com/efog82/Atlas/actions/runs/41326861929/artifacts/13091488308) · [Validação completa](docs/VALIDACAO.md).

## Funcionalidades no código

- **Início:** resumo diário, compromissos, tarefas abertas e atalhos.
- **Atlas IA:** inferência local por llama.cpp ou MediaPipe CPU, streaming, interrupção, histórico Room, várias conversas, contexto limitado por tokens e verificações preventivas de memória. Sem respostas simuladas.
- **Agente:** consultas à agenda, tarefas, diário, notas, biblioteca e memórias; JSON com ferramentas permitidas e argumentos validados. Confirmação separada antes de criar tarefas, eventos ou memórias. Sem ferramentas destrutivas.
- **Agenda:** mês/semana/dia, recorrências diárias/semanais/mensais, edição de séries e CRUD de tarefas. Alarmes locais e recuperação após reinício; fallback WorkManager quando não há permissão para alarmes exatos.
- **Diário:** registos datados, categorias, etiquetas e pesquisa. Não é convertido automaticamente em memória.
- **Estudos:** apontamentos por tema, TXT UTF-8/PDF com texto selecionável, pesquisa, referências por página/secção, resumos e revisão pela IA, progresso manual persistente. Citações recuperadas separadas do texto gerado.
- **Conhecimento:** biblioteca de fontes públicas com URL/revisão/data, cache offline, atualização em rede não tarifada, pesquisa local em documentos e citações separadas da explicação gerada.
- **Mais:** hábitos, memórias autorizadas editáveis/exportáveis, gestão GGUF/.task, temas, limites de contexto/resposta/RAM, bloqueio biométrico/credenciais e backups AES-256-GCM.

A versão 0.4 declara `INTERNET` e `ACCESS_NETWORK_STATE` para a consulta opcional de fontes públicas. O cliente envia apenas termos de uma pergunta pública nova ou IDs de páginas públicas; não recebe histórico, agenda, diário, memórias ou documentos. A inferência GGUF e MediaPipe continua inteiramente local. Online pode ser desligado em Conhecimento; sem ligação, só são usadas fontes locais. Não há Firebase, APIs pagas nem servidor próprio obrigatório. O modelo continua a ser importado pelo seletor Android/USB.

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

Versões fixadas: AGP 8.9.2, Kotlin/Compose compiler 2.1.20, KSP 2.1.20-1.0.32, Compose BOM 2025.04.01, Room 2.7.1, WorkManager 2.10.1, MediaPipe tasks-genai 0.10.24. [Referências oficiais e estado da verificação](docs/DEPENDENCIAS.md).

## Instalar APK e modelo

1. Instala o APK compilado num Android 13+ ARM64, autorizando essa origem, ou usa:

   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

2. Abre **Mais → Modelos**. A app apresenta as opções e os limites efetivamente observados. Nos exemplos de CPU ensaiados, **Gemma 3 1B IT Q4_K_M** deu respostas mais consistentes. **Qwen3 0.6B Q8_0** passou os testes de formato/thinking, mas ainda errou factos; **Qwen2.5 0.5B Q4_K_M** também mostrou qualidade factual limitada. Isto não é uma medição de qualidade geral nem validação no Redmi.
3. Obtém um dos GGUF pelos links da app: Gemma 3 (806 MB de pesos), Qwen3 oficial (639 MB) ou a opção Qwen2.5 mais pequena (491 MB). Os links Qwen3/Gemma estão fixados nas revisões testadas. Lê a licença: Apache 2.0 para Qwen e termos próprios Gemma. Usa navegador/computador ou USB e importa um único GGUF. Fragmentos `-00001-of-...` devem ser unidos previamente no computador com `llama-gguf-split`.
4. Toca em **Importar GGUF ou .task** e depois **Carregar**. A importação calcula SHA-256; compara-o com o fornecedor para verificar a origem. O cabeçalho é validado antes da importação e a arquitetura pelo motor ao carregar. Outros GGUF dependem do suporte na versão fixada do motor e da RAM.
5. Autoriza notificações e, para precisão horária, alarmes exatos em **Mais → Definições**.
6. Ativa modo de avião e envia uma pergunta em **Atlas IA**. Sem modelo carregado, a app pede a instalação e não fabrica respostas.

**MediaPipe:** usa [Gemma 3 1B .task](https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/d448835d84411a08f577e25f3afebe528402d73b/Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task?download=true), cerca de 555 MB. Inicia sessão e aceita os termos Gemma no fornecedor. Importa o ficheiro com o nome original, define orçamento de pelo menos 2048 MB e carrega-o. Contexto máximo 2048; o motor é escolhido pelo formato, sem ativar GPU. [Instruções e limites específicos](docs/MEDIAPIPE_E_FACTUAL.md).

**Modo factual:** deixa a opção ativa e desativa amostragem personalizada para usar os novos valores por família. Em Mais → Conhecimento, mantém «Exigir fontes» para perguntas factuais apoiadas por excertos locais/online. Amostragem não garante exatidão; os testes levaram a manter os valores do fabricante em Gemma 3 e Qwen2.5, pois reduzi-los não melhorou os exemplos ensaiados.

**Hardware:** não há medições físicas de RAM, rapidez ou energia no Redmi nesta sessão. Os tamanhos acima são apenas dos pesos; contexto, buffers e sistema precisam de memória adicional. A verificação de RAM da app é preventiva, não uma quota do processo. Começa com contexto pequeno e modo non-thinking; o Android pode terminar processos por falta de RAM. GPU/NPU continuam desativadas.

O APK é debug e usa a chave do ambiente de build, que pode diferir da instalação anterior. Se o Android recusar atualizar por assinatura incompatível, exporta uma cópia encriptada e confirma que tens a palavra-passe antes de considerar desinstalar: desinstalar apaga o armazenamento privado.

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
python3 scripts/test-search-sql.py     # queries reais em SQLite host, sem Room Android
./gradlew testDebugUnitTest            # testes JVM executados com sucesso no GitHub Actions
./gradlew connectedDebugAndroidTest   # 17 testes instrumentados compilados; execução requer Android ARM64 e modelos
```

Os testes de inferência requerem modelos importados; o teste MediaPipe requer especificamente o Gemma 3 1B .task. Sem pesos ficam **ignorados**, não aprovados. O teste de abertura pressupõe bloqueio da app desligado. Testes de Room/backup usam bases próprias. [Procedimento e resultados](docs/VALIDACAO.md).

O [workflow Android](.github/workflows/android.yml) instala SDK/NDK, prepara llama.cpp, executa testes JVM/lint, compila e publica APK e relatórios no artefacto `atlas-personal-debug-arm64`. O runner x86 não executa testes instrumentados. A tarefa adicional `model-integration` compila o renderizador/sampler partilhados, descarrega revisões GGUF fixadas, verifica SHA-256, executa gerações reais, valida-as no parser Kotlin da app e audita o idioma. A sonda de Wikipédia regista separadamente uma consulta pública real. A sonda `.task` regista metadados e acesso à descarga; não executa o motor Android e não contorna autenticação. Falhas externas não são apresentadas como validação online. Estes ensaios CPU não substituem testes Android/Redmi. Repositório GitHub: [efog82/Atlas](https://github.com/efog82/Atlas). Consulta [Actions](https://github.com/efog82/Atlas/actions) para acompanhar a compilação. Quando a execução terminar com sucesso, descarrega o artefacto `atlas-personal-debug-arm64`. Uma compilação bem-sucedida não demonstra execução em dispositivo.

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
