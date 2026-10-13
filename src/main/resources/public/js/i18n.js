const translations = {
  en: {
    // General
    loading: "Loading CraftPanel Web...",
    dashboard: "Dashboard",
    servers: "Servers",
    instance: "Instance",
    console: "Real-Time Console",
    files: "File Manager",
    plugins: "Installed Plugins",
    modrinth: "Modrinth Store",
    backups: "Backups & Recovery",
    monitoring: "Monitoring",
    administration: "Administration",
    usersAndRoles: "Users & Roles",
    auditLogs: "Audit Logs",
    settings: "Settings & Diagnostics",
    selectedInstance: "SELECTED INSTANCE",
    selectServerPrompt: "Select a server...",
    logout: "Sign Out",
    save: "Save",
    cancel: "Cancel",
    confirm: "Confirm",
    delete: "Delete",
    edit: "Edit",
    create: "Create",
    search: "Search",
    refresh: "Refresh",
    actions: "Actions",
    status: "Status",
    name: "Name",
    version: "Version",
    software: "Software",
    port: "Port",
    players: "Players",
    uptime: "Uptime",
    inactive: "Inactive",
    quickActions: "Quick Actions",
    language: "Language",
    english: "English",
    portuguese: "Português",

    // Auth & Setup
    initialSetup: "Initial Setup",
    initialSetupDesc: "Create the initial administrator account to get started",
    username: "Username",
    displayName: "Display Name",
    password: "Password",
    confirmPassword: "Confirm Password",
    completeAndStart: "Complete Setup and Start Panel",
    panelAccess: "Sign In",
    panelAccessSubtitle: "Modern Minecraft server management panel",
    signIn: "Sign In",

    // Dashboard
    registeredInstances: "Registered Instances",
    onlineServers: "Online Servers",
    connectedPlayers: "Connected Players",
    allocatedRam: "Allocated RAM",
    activeInstances: "Active Instances",
    instancesOverview: "Overview and health status of all managed instances",
    newInstance: "New Instance",
    loadingServers: "Loading servers...",
    noInstancesYet: "No server instances registered",
    noInstancesDesc: "Deploy your first Paper, Purpur, Folia, Spigot, or Vanilla server in seconds.",
    createFirstServer: "Create Server Now",
    start: "Start",
    stop: "Stop",
    restart: "Restart",
    kill: "Kill",
    manage: "Manage",

    // Server list & creation
    createServerTitle: "Deploy New Minecraft Server",
    instanceName: "Instance Name",
    description: "Description",
    selectSoftware: "Server Software",
    minecraftVersion: "Minecraft Version",
    buildNumber: "Build Number",
    minMemory: "Minimum Memory (MB)",
    maxMemory: "Maximum Memory (MB)",
    serverPort: "Server Port",
    jvmArgs: "Additional JVM Flags",
    acceptEula: "I agree to the Minecraft End User License Agreement (EULA)",
    installing: "Deploying...",
    deployServer: "Deploy Server",

    // Console
    liveTerminal: "Interactive Server Terminal",
    clearConsole: "Clear View",
    autoscroll: "Auto-scroll",
    commandPlaceholder: "Type a Minecraft command (e.g. list, op, help)...",
    sendCommand: "Send",

    // Files
    uploadFile: "Upload File",
    newFile: "New File",
    newFolder: "New Folder",
    size: "Size",
    lastModified: "Last Modified",
    editingFile: "Editing File",
    saveFile: "Save Changes",

    // Plugins & Modrinth
    pluginManager: "Plugin Manager",
    searchModrinth: "Browse Modrinth Plugins",
    installLocalJar: "Upload JAR",
    dependencies: "Dependencies",
    enabled: "Enabled",
    disabled: "Disabled",
    searchPlaceholder: "Search plugins on Modrinth (e.g. Essentials, LuckPerms, Vault)...",
    allVersions: "All Versions",
    allPlatforms: "All Loaders",
    compatibility: "Compatibility",
    installPlugin: "Install Plugin",
    installed: "Installed",

    // Backups
    createBackup: "Create Backup",
    backupNote: "Notes / Description",
    createBackupBtn: "Create Backup Now",
    restoreBackup: "Restore",
    downloadBackup: "Download",
    deleteBackup: "Delete",
    confirmRestore: "Are you sure you want to restore this backup? The server will be stopped and files restored.",

    // Monitoring
    systemHostMetrics: "Host System Metrics",
    cpuUsage: "CPU Usage",
    ramUsage: "RAM Usage",
    diskUsage: "Disk Storage",
    pingLatency: "Server Ping & Latency",

    // Users
    usersTitle: "User Accounts",
    rolesTitle: "Roles & Permissions",
    createUser: "New User",
    role: "Role",
    resetPassword: "Reset Password",
    serverPermissions: "Server Permissions",

    // Audit
    auditTitle: "System Audit Logs",
    action: "Action",
    user: "User",
    ipAddress: "IP Address",
    timestamp: "Timestamp",

    // Notifications
    success: "Success",
    error: "Error",
    info: "Info"
  },

  pt: {
    // General
    loading: "Carregando CraftPanel Web...",
    dashboard: "Dashboard",
    servers: "Servidores",
    instance: "Instância",
    console: "Console em Tempo Real",
    files: "Gerenciador de Arquivos",
    plugins: "Plugins Instalados",
    modrinth: "Loja Modrinth",
    backups: "Backups & Restauração",
    monitoring: "Monitoramento",
    administration: "Administração",
    usersAndRoles: "Usuários & Funções",
    auditLogs: "Logs de Auditoria",
    settings: "Diagnóstico & Configurações",
    selectedInstance: "INSTÂNCIA SELECIONADA",
    selectServerPrompt: "Selecione um servidor...",
    logout: "Encerrar Sessão",
    save: "Salvar",
    cancel: "Cancelar",
    confirm: "Confirmar",
    delete: "Excluir",
    edit: "Editar",
    create: "Criar",
    search: "Pesquisar",
    refresh: "Atualizar",
    actions: "Ações",
    status: "Status",
    name: "Nome",
    version: "Versão",
    software: "Software",
    port: "Porta",
    players: "Jogadores",
    uptime: "Tempo Ativo",
    inactive: "Inativo",
    quickActions: "Ações Rápidas",
    language: "Idioma",
    english: "English",
    portuguese: "Português",

    // Auth & Setup
    initialSetup: "Configuração Inicial",
    initialSetupDesc: "Crie a conta do primeiro administrador do sistema",
    username: "Nome de Usuário",
    displayName: "Nome de Exibição",
    password: "Senha",
    confirmPassword: "Confirmar Senha",
    completeAndStart: "Concluir e Iniciar Painel",
    panelAccess: "Acesso ao Painel",
    panelAccessSubtitle: "Gerenciamento moderno de servidores Minecraft",
    signIn: "Entrar no Painel",

    // Dashboard
    registeredInstances: "Instâncias Cadastradas",
    onlineServers: "Servidores Online",
    connectedPlayers: "Jogadores Conectados",
    allocatedRam: "RAM Alocada",
    activeInstances: "Instâncias Ativas",
    instancesOverview: "Visão geral do estado das instâncias gerenciadas",
    newInstance: "Nova Instância",
    loadingServers: "Carregando servidores...",
    noInstancesYet: "Nenhuma instância cadastrada",
    noInstancesDesc: "Crie seu primeiro servidor Minecraft Paper, Purpur, Folia ou Spigot em poucos segundos.",
    createFirstServer: "Criar Servidor Agora",
    start: "Iniciar",
    stop: "Parar",
    restart: "Reiniciar",
    kill: "Forçar Parada",
    manage: "Gerenciar",

    // Server list & creation
    createServerTitle: "Criar Novo Servidor Minecraft",
    instanceName: "Nome da Instância",
    description: "Descrição",
    selectSoftware: "Software do Servidor",
    minecraftVersion: "Versão do Minecraft",
    buildNumber: "Número da Build",
    minMemory: "Memória Mínima (MB)",
    maxMemory: "Memória Máxima (MB)",
    serverPort: "Porta do Servidor",
    jvmArgs: "Argumentos Adicionais da JVM",
    acceptEula: "Aceito o Contrato de Licença de Usuário Final (EULA)",
    installing: "Instalando...",
    deployServer: "Instalar Servidor",

    // Console
    liveTerminal: "Terminal Interativo do Servidor",
    clearConsole: "Limpar Visualização",
    autoscroll: "Rolagem Automática",
    commandPlaceholder: "Digite um comando Minecraft (ex: list, op, help)...",
    sendCommand: "Enviar",

    // Files
    uploadFile: "Enviar Arquivo",
    newFile: "Novo Arquivo",
    newFolder: "Nova Pasta",
    size: "Tamanho",
    lastModified: "Última Modificação",
    editingFile: "Editando Arquivo",
    saveFile: "Salvar Alterações",

    // Plugins & Modrinth
    pluginManager: "Gerenciador de Plugins",
    searchModrinth: "Buscar na Loja Modrinth",
    installLocalJar: "Enviar JAR",
    dependencies: "Dependências",
    enabled: "Ativo",
    disabled: "Desativado",
    searchPlaceholder: "Pesquisar plugins no Modrinth (ex: Essentials, LuckPerms, Vault)...",
    allVersions: "Todas as Versões",
    allPlatforms: "Todos os Loaders",
    compatibility: "Compatibilidade",
    installPlugin: "Instalar Plugin",
    installed: "Instalado",

    // Backups
    createBackup: "Criar Backup",
    backupNote: "Notas / Descrição",
    createBackupBtn: "Criar Backup Agora",
    restoreBackup: "Restaurar",
    downloadBackup: "Baixar",
    deleteBackup: "Excluir",
    confirmRestore: "Tem certeza de que deseja restaurar este backup? O servidor será interrompido e os arquivos substituídos.",

    // Monitoring
    systemHostMetrics: "Métricas do Sistema Hospedeiro",
    cpuUsage: "Uso de CPU",
    ramUsage: "Uso de Memória RAM",
    diskUsage: "Armazenamento em Disco",
    pingLatency: "Ping e Latência do Servidor",

    // Users
    usersTitle: "Contas de Usuários",
    rolesTitle: "Funções & Permissões",
    createUser: "Novo Usuário",
    role: "Função",
    resetPassword: "Redefinir Senha",
    serverPermissions: "Permissões por Servidor",

    // Audit
    auditTitle: "Logs de Auditoria do Sistema",
    action: "Ação",
    user: "Usuário",
    ipAddress: "Endereço IP",
    timestamp: "Data e Hora",

    // Notifications
    success: "Sucesso",
    error: "Erro",
    info: "Informação"
  }
};

let currentLang = localStorage.getItem('craftpanel_lang') || 'en';

export const i18n = {
  getLanguage() {
    return currentLang;
  },

  setLanguage(lang) {
    if (translations[lang]) {
      currentLang = lang;
      localStorage.setItem('craftpanel_lang', lang);
      window.dispatchEvent(new CustomEvent('language:changed', { detail: lang }));
    }
  },

  t(key, fallback = '') {
    const dict = translations[currentLang] || translations.en;
    if (dict && dict[key]) {
      return dict[key];
    }
    const enDict = translations.en;
    if (enDict && enDict[key]) {
      return enDict[key];
    }
    return fallback || key;
  }
};
