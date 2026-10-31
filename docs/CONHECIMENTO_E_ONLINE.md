# Atlas 0.4 — fontes online e biblioteca offline

O Atlas procura informação antes de pedir uma explicação ao modelo. Com ligação validada à Internet e a opção online ligada, pode consultar a Wikipédia em português para perguntas gerais. Sem ligação, utiliza apenas as fontes guardadas no telemóvel. O motor GGUF continua local: não foi substituído por um serviço de IA online.

## O que funciona

- Consulta automática de excertos relevantes dos TXT/PDF importados e dos apontamentos.
- Para pedidos pessoais, consulta local de diário, memórias explicitamente autorizadas, tarefas e agenda. O planeamento pode incluir a agenda do dia pedido e preferências guardadas. Isto não cria memórias nem altera registos.
- Pesquisa enciclopédica online para perguntas gerais claras, com cache dos excertos, título, URL da revisão, data da revisão, data de obtenção e hash do conteúdo.
- Cartões de fontes no chat com citações literais, referências `[F1]` etc. e ligação à revisão pública. Fontes locais indicam página/secção ou o tipo de registo.
- Reutilização offline da biblioteca pública obtida anteriormente. Os textos importados não são enviados para obter fontes online.
- Atualização periódica das fontes públicas guardadas, em lotes de até 20 das mais antigas. Usa WorkManager com rede não tarifada (normalmente Wi-Fi) e bateria disponível; o Android pode adiar o trabalho. Há atualização manual.
- Página **Mais → Conhecimento** para controlar pesquisa online, consultas pessoais automáticas, exigência de fontes, atualização e eliminação de fontes com confirmação.
- Sem fontes suficientes e com «Exigir fontes» ligado, a aplicação comunica que não encontrou suporte, em vez de solicitar uma resposta factual livre ao modelo. Saudações e conversas de apoio não dependem de existir uma fonte factual.
- Sem GGUF carregado, os excertos continuam disponíveis; a explicação gerada é opcional e exige um modelo local.

## Limites claros

A fonte online desta versão é **Wikipédia em português**, não uma pesquisa geral de toda a Web. Não inclui serviço meteorológico, preços/cotações, notícias ou resultados em tempo real. A app indica essa limitação nos pedidos reconhecidos, em vez de tratar um artigo geral como informação atual. A biblioteca não é uma enciclopédia completa pré-instalada: sem Internet, só contém o que já foi obtido ou importado.

A atualização modifica a biblioteca de fontes públicas, **não treina nem atualiza automaticamente os pesos do modelo**. Uma página recente pode conter erros; a data de obtenção não significa que todos os seus factos sejam atuais.

RAG não garante verdade. O modelo pode interpretar mal os excertos ou citar uma fonte que não apoia a afirmação. A app valida se os identificadores citados existem e avisa quando faltam ou são inválidos; não declara correção semântica a partir de palavras-chave. A resposta gerada é preservada, sem a substituir por factos pré-programados. Os cartões mostram os textos recuperados independentemente da explicação.

O contexto factual de cada pedido é reconstruído com os excertos atuais; respostas antigas do assistente não contam como evidência. Um seguimento como «explica melhor» pode reutilizar o último conjunto de fontes desta conversa, localmente. Os instantâneos das citações permanecem no histórico mesmo quando uma página pública é atualizada.

## Privacidade e comportamento automático

O modo online e as consultas pessoais automáticas começam ligados nesta versão, de acordo com o pedido do utilizador, e podem ser desligados em Conhecimento. Desligar online impede novas ligações do cliente e cancela o trabalho periódico. As permissões Android `INTERNET` e `ACCESS_NETWORK_STATE` passam a estar declaradas; a inferência não precisa delas.

O cliente aceita somente uma pergunta/tema público novo ou IDs numéricos de páginas públicas. Não recebe agenda, diário, memórias, documentos, histórico ou respostas do modelo. A pesquisa automática usa regras conservadoras para impedir pedidos pessoais, contactos, números longos e excertos copiados. Quando encontra excertos privados relevantes, não transforma esses dados numa pesquisa externa. Pedidos ambíguos podem precisar do botão **Pesquisar fontes**, onde o utilizador escreve explicitamente um tema público.

O fornecedor recebe os termos da consulta e os metadados normais de uma ligação HTTPS, incluindo IP. Perguntas digitadas podem revelar interesses: desligar a opção online é a forma de manter todas as consultas no dispositivo. A app não envia os registos locais para personalizar a pesquisa.

O endereço HTTP é fixo em `https://pt.wikipedia.org/w/api.php`, sem cookies, autenticação, tokens ou APIs pagas. Não segue redirecionamentos automaticamente. Ligações para fontes usam apenas URLs canónicas de revisões Wikipédia; texto gerado não abre endereços nem executa ferramentas.

Criar/alterar registos pessoais continua sujeito aos mecanismos anteriores de confirmação. Atualizar o cache público não altera os registos pessoais. Eliminar uma fonte exige confirmação; um refresh em curso não recria fontes apagadas. Uma pesquisa futura pode voltar a obter o artigo, se a consulta online continuar autorizada.

## Dados, recuperação e energia

Os dados novos reutilizam as tabelas Room `records`/`sections`, com um tipo distinto `knowledge-source`; não há alteração de esquema nem migração destrutiva. As fontes públicas ficam separadas das memórias pessoais. Backups protegidos incluem fontes, secções e referências do chat, e a restauração continua a aceitar cópias anteriores. Preferências e pesos GGUF continuam fora da cópia.

A recuperação é lexical, com frequência de termos, ponderação de título e cobertura da pergunta, semelhante a BM25. **Não foram integrados embeddings nem um segundo modelo de vetorização.** São consultados candidatos limitados e excertos próximos das palavras encontradas, em vez de carregar toda a biblioteca em RAM. Até oito termos e quatro fontes entram na seleção; o texto é reduzido para caber no contexto configurado. Isto não encontra todas as paráfrases e não prova que um excerto responda completamente à pergunta.

O modo factual com fontes usa pelo menos 1024 tokens de contexto para gerar uma explicação. Com contexto menor, mostra os excertos e indica o ajuste necessário. A execução GGUF, perfis CPU, interrupção, templates Qwen3/Gemma e gestão de modelos mantêm os controlos anteriores.

Os textos públicos são atribuídos à Wikipédia e à revisão de origem; aplicam-se os termos CC BY-SA correspondentes. O cache não é anunciado como base de «factos verdadeiros» verificados.

## Testes

- `KnowledgeTest`: bloqueio de pedidos pessoais/contactos, separação do tema público, zero ligações com online desligado, parsing da API, refresh de 20 páginas, ranking lexical, integridade dos excertos e avisos de citações sem reescrever a resposta.
- `scripts/test-search-sql.py`: executa as queries reais do fonte em SQLite host. Detetou/cobre um erro anterior de escape SQL nas pesquisas; verifica `%` literal e excertos encontrados a meio do documento. Não equivale à execução de Room em Android.
- `DatabaseAgentTest`: teste Android de persistência da biblioteca, separação do diário, refresh sem alterar dados privados e prevenção de recriação após eliminar; compilação não equivale a execução no dispositivo.
- `BackupRestoreTest`: inclui secções de uma fonte pública na cópia/recuperação protegida.
- `scripts/check-knowledge-live.py`: consulta pública real à Wikipédia, com resultado integral e estado de sucesso/falha registados. É uma sonda informativa: falhas de rede não são transformadas em aprovação.
- Integração GGUF real: acrescenta um documento **fictício de ensaio**, não distribuído como conhecimento factual da app. Os modelos recebem uma informação nova e a sua utilização/citação é registada. Isto avalia grounding num caso controlado; não certifica precisão geral.

Os resultados executados, o APK e as limitações do ensaio Android estão em [VALIDACAO.md](VALIDACAO.md). Não foram realizadas medições no Redmi nesta sessão.
