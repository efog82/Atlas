# Atlas Personal

**Versão 0.2.2:** cabeçalho de data/hora retirado das mensagens de conversa e capturas de ecrã permitidas. [Correção e testes](docs/CORRECAO_HORA.md).

**Versão 0.2.1:** corrigidas propostas indevidas em conversa e repetição de ações canceladas, incluindo conversas antigas. [Diagnóstico e testes](docs/CORRECAO_CONVERSA.md).

**Versão 0.2:** contexto persistente, KV cache por conversa, interrupção dentro do grafo CPU, perfis e comparação local com métricas. [Análise e testes de desempenho](docs/DESEMPENHO.md). Não há resultados de velocidade do Redmi inventados.

Aplicação Android nativa para organização pessoal e conversas com um modelo GGUF local. Kotlin, Compose/Material 3, MVVM, StateFlow, Room, WorkManager, AlarmManager e JNI/llama.cpp. Android 13+ (API 33), exclusivamente ARM64.

**Estado real:** APK debug ARM64 compilado com sucesso no GitHub Actions, com 51 testes JVM, testes nativos de cache, lint, verificação de kernels -O3 e sete verificações estáticas aprovados. [Execução validada](https://github.com/efog82/Atlas/actions/runs/41224681530) · [Descarregar APK e relatórios (ZIP)](https://github.com/efog82/Atlas/actions/runs/41224681530/artifacts/13051591220).

Extrai o ZIP e instala `app/build/outputs/apk/debug/app-debug.apk`. A versão 0.2.2 ainda precisa de ensaio no Redmi/Android ARM64 com GGUF instalado; os testes instrumentados não foram executados pelo runner x86. O modelo não está incluído no APK. [Relatório completo](docs/VALIDACAO.md).

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

   O script obtém a tag `b5046` de `ggml-org/llama.cpp`, regista o commit efetivo em `native/llama-revision.txt` e copia a licença original para os assets. O código JNI usa tokenização, decode e sampling reais.

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

2. Abre **Mais → Modelos**. Recomendação configurada: **Qwen2.5-0.5B-Instruct, GGUF Q4_K_M**, pequeno e multilingue, incluindo português. A licença indicada pelo projeto Qwen é Apache 2.0. A ficha e o GGUF não puderam ser consultados/descarregados nesta sessão; a seleção não equivale a validação em hardware.
3. Obtém o GGUF na [página oficial Qwen](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF), pelo navegador ou computador. Importa o ficheiro único `qwen2.5-0.5b-instruct-q4_k_m.gguf`. Fragmentos `-00001-of-...` devem ser unidos previamente no computador com `llama-gguf-split`.
4. Toca em **Importar GGUF** e depois **Carregar**. A importação calcula SHA-256; compara-o com o fornecedor para verificar a origem. O cabeçalho é validado antes da importação e a arquitetura pelo motor ao carregar. Outros GGUF dependem do suporte na versão fixada do motor e da RAM.
5. Autoriza notificações e, para precisão horária, alarmes exatos em **Mais → Definições**.
6. Ativa modo de avião e envia uma pergunta em **Atlas IA**. Sem modelo carregado, a app pede a instalação e não fabrica respostas.

**Hardware:** nenhum dispositivo disponível para medir RAM, velocidade, aquecimento ou compatibilidade real. A interface sugere aproximadamente **1–1,5 GB de RAM livre** e contexto de 2048 tokens para o modelo recomendado; é uma estimativa. Confirma o tamanho real do GGUF na origem. Recomenda-se testar com pelo menos 4 GB de RAM. O orçamento configurado não é uma quota imposta ao processo; o Android pode terminar a app por falta de RAM. Não há aceleração GPU/NPU.

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
./gradlew connectedDebugAndroidTest   # 14 testes instrumentados compilados; execução requer Android ARM64 e GGUF
```

O teste de inferência procura um GGUF já importado. Sem ele fica **ignorado**, não aprovado. O teste de abertura pressupõe bloqueio da app desligado. Testes de Room/backup usam bases próprias. [Procedimento e resultados](docs/VALIDACAO.md).

O [workflow Android](.github/workflows/android.yml) instala SDK/NDK, prepara llama.cpp, executa testes JVM/lint, compila e publica APK e relatórios no artefacto `atlas-personal-debug-arm64`. O runner x86 não executa testes instrumentados. Repositório GitHub: [efog82/Atlas](https://github.com/efog82/Atlas). Consulta [Actions](https://github.com/efog82/Atlas/actions) para acompanhar a compilação. Quando a execução terminar com sucesso, descarrega o artefacto `atlas-personal-debug-arm64`. A presença do workflow não significa que o APK já tenha sido compilado ou testado num dispositivo.

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
