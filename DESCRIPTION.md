<center>
<h2>CraftPanel Web</h2>
<p><strong>Painel Web Moderno, Seguro e Completo para Administração de Servidores Minecraft</strong></p>
<p><em>Desenvolvido por lHawk_</em></p>
</center>

<hr>

<h3>1. Visão Geral</h3>
<p>O <strong>CraftPanel Web</strong> é uma aplicação web autônoma projetada para hospedar, monitorar e administrar servidores Minecraft diretamente pelo navegador, com foco em facilidade de uso, alta performance e controle granular de processos locais.</p>

<p>Inspirado na usabilidade dos principais painéis de hospedagem do mercado, o CraftPanel Web elimina a necessidade de comandos manuais no terminal, oferecendo uma interface limpa, intuitiva e totalmente em tempo real.</p>

<hr>

<h3>2. Funcionalidades Principais</h3>
<ul>
<li><strong>Gerenciamento Completo de Ciclo de Vida:</strong> Inicie, pare de forma graciosa (comando <code>stop</code>), reinicie ou encerre processos de servidores com detecção automática de falhas.</li>
<li><strong>Console Interativo em Tempo Real:</strong> Terminal web com WebSockets, suporte a cores, busca de logs, pausa de rolagem e execução segura de comandos autorizados.</li>
<li><strong>Gerenciador de Arquivos Avançado:</strong> Navegação rápida, visualização e edição de configurações (<code>server.properties</code>, <code>bukkit.yml</code>, <code>spigot.yml</code>, <code>paper-global.yml</code>), criação de pastas, uploads e downloads, com proteção rigorosa contra path traversal.</li>
<li><strong>Gerenciador de Plugins &amp; Loja Modrinth:</strong> Detecção automática de metadados de plugins JAR (nome, versão, autor, dependências), upload manual e integração oficial com a API do Modrinth para pesquisa, filtros de versão e instalação em um clique.</li>
<li><strong>Matriz de Compatibilidade Inteligente:</strong> Suporte comprovado de versões do Minecraft 1.8 até a versão mais recente, avaliando plataformas Paper, Purpur, Spigot, Bukkit e Folia.</li>
<li><strong>Instalador de Servidores Automatizado:</strong> Assistente integrado para download direto de builds oficiais de Paper, Purpur, Folia e Vanilla, com configuração de memória JVM e aceitação de EULA.</li>
<li><strong>Controle de Acesso Granular (RBAC):</strong> Criação de usuários, funções personalizadas e concessão de permissões específicas por servidor.</li>
<li><strong>Monitoramento em Tempo Real:</strong> Gráficos e indicadores contínuos de uso de CPU, memória RAM da JVM, espaço em disco e jogadores conectados.</li>
<li><strong>Backups &amp; Restauração:</strong> Criação de cópias compactadas em ZIP com verificação de integridade e restauração assistida.</li>
<li><strong>Auditoria Completa:</strong> Registro persistente de todas as ações administrativas, alterações de arquivos e eventos de segurança.</li>
</ul>

<hr>

<h3>3. Compatibilidade de Plataformas e Versões</h3>
<table>
<tr>
<th>Plataforma</th>
<th>Versões Suportadas</th>
<th>Status de Compatibilidade</th>
</tr>
<tr>
<td><strong>Paper</strong></td>
<td>1.8.8 até a versão mais recente</td>
<td>Totalmente Compatível</td>
</tr>
<tr>
<td><strong>Purpur</strong></td>
<td>1.16.5 até a versão mais recente</td>
<td>Totalmente Compatível</td>
</tr>
<tr>
<td><strong>Folia</strong></td>
<td>1.19.4 até a versão mais recente</td>
<td>Totalmente Compatível</td>
</tr>
<tr>
<td><strong>Spigot / Bukkit</strong></td>
<td>1.8 até a versão mais recente</td>
<td>Totalmente Compatível</td>
</tr>
<tr>
<td><strong>Vanilla</strong></td>
<td>1.8 até a versão mais recente</td>
<td>Compatível (Processo e Arquivos)</td>
</tr>
</table>

<hr>

<h3>4. Requisitos do Sistema</h3>
<ul>
<li><strong>Java Runtime:</strong> Java 21 LTS ou superior instalado no host.</li>
<li><strong>Sistemas Operacionais:</strong> Windows 10/11, Windows Server, Linux (Ubuntu, Debian, CentOS, Fedora).</li>
<li><strong>Navegadores:</strong> Chrome, Firefox, Edge, Safari e navegadores modernos com suporte a WebSockets e ES Modules.</li>
<li><strong>Dependências Externas:</strong> Nenhuma dependência externa obrigatória. SQLite e Jetty já estão embutidos no pacote distribuível.</li>
</ul>

<hr>

<h3>5. Instalação e Inicialização</h3>
<ol>
<li>Baixe o artefato executável <code>craftpanel-web.jar</code>.</li>
<li>Abra o terminal no diretório do arquivo.</li>
<li>Execute o comando de inicialização:
<pre><code>java -jar craftpanel-web.jar</code></pre>
</li>
<li>Acesse o painel no navegador através do endereço local padrão:
<pre><code>http://127.0.0.1:8080</code></pre>
</li>
<li>Na primeira execução, o assistente inicial solicitará a criação da conta do primeiro administrador.</li>
</ol>

<hr>

<h3>6. Configurações (craftpanel.yml)</h3>
<p>As configurações principais podem ser ajustadas no arquivo <code>craftpanel.yml</code> gerado automaticamente na raiz:</p>
<pre><code>server:
  host: "127.0.0.1"
  port: 8080

security:
  remote_access_enabled: false
  session_timeout_minutes: 60
  require_secure_cookies: false

storage:
  data_directory: "./data"
  database: "./data/database.db"
  backup_directory: "./data/backups"
  servers_directory: "./data/servers"

modrinth:
  enabled: true
  user_agent: "CraftPanel-Web/1.0"

monitoring:
  refresh_interval_seconds: 3

logging:
  level: "INFO"</code></pre>

<hr>

<h3>7. Segurança e Boas Práticas</h3>
<blockquote>
<p><strong>Aviso de Segurança:</strong> Por padrão, o painel escuta exclusivamente na interface de loopback (<code>127.0.0.1</code>). A exposição para redes locais ou públicas (<code>0.0.0.0</code>) exige autorização explícita na configuração. Recomenda-se o uso de túneis autenticados ou VPN para conexões remotas seguras.</p>
</blockquote>

<hr>

<h3>8. Suporte e Comunidade</h3>
<p>Para dúvidas, relatórios e suporte da comunidade, acesse:</p>
<p><a href="https://minebuilders.com.br/discord">Comunidade MineBuilders</a></p>
