package dev.mago.android

import android.content.Context
import dev.mago.android.common.DefaultDispatcherProvider
import dev.mago.android.common.DispatcherProvider
import dev.mago.android.database.InstallationStateMapper
import dev.mago.android.database.MagoDatabase
import dev.mago.android.database.ModuleDatabaseMapper
import dev.mago.android.database.RoomInstallationStateRepository
import dev.mago.android.database.RoomModuleLocalStore
import dev.mago.android.installation.BootstrapCoordinator
import dev.mago.android.installation.BootstrapCoordinatorImpl
import dev.mago.android.installation.InstallationStateRepository
import dev.mago.android.installation.TermuxGateway
import dev.mago.android.metasploit.MetasploitConnectionRepository
import dev.mago.android.metasploit.MetasploitConsoleRepository
import dev.mago.android.metasploit.MetasploitModuleRepository
import dev.mago.android.metasploit.MetasploitOperationsRepository
import dev.mago.android.metasploit.ModuleLocalStore
import dev.mago.android.rpc.MessagePackRpcCodec
import dev.mago.android.rpc.MetasploitConnectionRepositoryImpl
import dev.mago.android.rpc.MetasploitConsoleRepositoryImpl
import dev.mago.android.rpc.MetasploitModuleRepositoryImpl
import dev.mago.android.rpc.MetasploitOperationsRepositoryImpl
import dev.mago.android.rpc.OkHttpRpcTransport
import dev.mago.android.rpc.RpcTransport
import dev.mago.android.security.AndroidKeystoreSecretStore
import dev.mago.android.security.InMemoryRpcTokenStore
import dev.mago.android.security.RpcEndpointPolicy
import dev.mago.android.security.RpcTokenStore
import dev.mago.android.security.SecretStore
import dev.mago.android.termux.TermuxGatewayImpl
import okhttp3.OkHttpClient

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = MagoDatabase.create(appContext)

    val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()
    private val installationStateRepository: InstallationStateRepository =
        RoomInstallationStateRepository(
            dao = database.installationStateDao(),
            mapper = InstallationStateMapper(),
        )

    val rpcTokenStore: RpcTokenStore = InMemoryRpcTokenStore()
    val secretStore: SecretStore = AndroidKeystoreSecretStore(appContext)
    val rpcTransport: RpcTransport = OkHttpRpcTransport(
        endpointPolicy = RpcEndpointPolicy(),
        client = OkHttpClient(),
        codec = MessagePackRpcCodec(),
        dispatcherProvider = dispatcherProvider,
    )
    val metasploitConnectionRepository: MetasploitConnectionRepository =
        MetasploitConnectionRepositoryImpl(rpcTransport, secretStore, rpcTokenStore)
    val metasploitModuleRepository: MetasploitModuleRepository =
        MetasploitModuleRepositoryImpl(rpcTransport, rpcTokenStore)
    val metasploitOperationsRepository: MetasploitOperationsRepository =
        MetasploitOperationsRepositoryImpl(rpcTransport, rpcTokenStore)
    val moduleLocalStore: ModuleLocalStore = RoomModuleLocalStore(
        catalogDao = database.moduleCatalogDao(),
        historyDao = database.moduleHistoryDao(),
        mapper = ModuleDatabaseMapper(),
    )
    val metasploitConsoleRepository: MetasploitConsoleRepository =
        MetasploitConsoleRepositoryImpl(rpcTransport, rpcTokenStore)
    val termuxGateway: TermuxGateway = TermuxGatewayImpl(appContext, dispatcherProvider)
    val bootstrapCoordinator: BootstrapCoordinator = BootstrapCoordinatorImpl(
        termuxGateway = termuxGateway,
        metasploitRepository = metasploitConnectionRepository,
        installationStateRepository = installationStateRepository,
        secretStore = secretStore,
    )
}
