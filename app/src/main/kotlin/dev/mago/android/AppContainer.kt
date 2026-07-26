package dev.mago.android

import android.content.Context
import dev.mago.android.common.DefaultDispatcherProvider
import dev.mago.android.common.DispatcherProvider
import dev.mago.android.database.InstallationStateMapper
import dev.mago.android.database.MagoDatabase
import dev.mago.android.database.RoomInstallationStateRepository
import dev.mago.android.installation.BootstrapCoordinator
import dev.mago.android.installation.BootstrapCoordinatorImpl
import dev.mago.android.installation.InstallationStateRepository
import dev.mago.android.installation.TermuxGateway
import dev.mago.android.metasploit.MetasploitConnectionRepository
import dev.mago.android.metasploit.MetasploitConsoleRepository
import dev.mago.android.metasploit.MetasploitJobRepository
import dev.mago.android.metasploit.MetasploitModuleRepository
import dev.mago.android.metasploit.MetasploitSessionRepository
import dev.mago.android.rpc.MessagePackRpcCodec
import dev.mago.android.rpc.MetasploitConnectionRepositoryImpl
import dev.mago.android.rpc.MetasploitConsoleRepositoryImpl
import dev.mago.android.rpc.MetasploitJobRepositoryImpl
import dev.mago.android.rpc.MetasploitModuleRepositoryImpl
import dev.mago.android.rpc.MetasploitSessionRepositoryImpl
import dev.mago.android.rpc.OkHttpRpcTransport
import dev.mago.android.rpc.RpcTransport
import dev.mago.android.rpc.SessionIoCoordinator
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
    private val sessionIoCoordinator = SessionIoCoordinator()
    val rpcTransport: RpcTransport = OkHttpRpcTransport(
        endpointPolicy = RpcEndpointPolicy(),
        client = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .build(),
        codec = MessagePackRpcCodec(),
        dispatcherProvider = dispatcherProvider,
    )
    val metasploitConnectionRepository: MetasploitConnectionRepository =
        MetasploitConnectionRepositoryImpl(rpcTransport, secretStore, rpcTokenStore)
    val metasploitModuleRepository: MetasploitModuleRepository =
        MetasploitModuleRepositoryImpl(rpcTransport, rpcTokenStore)
    val metasploitConsoleRepository: MetasploitConsoleRepository =
        MetasploitConsoleRepositoryImpl(rpcTransport, rpcTokenStore)
    val metasploitJobRepository: MetasploitJobRepository =
        MetasploitJobRepositoryImpl(rpcTransport, rpcTokenStore)
    val metasploitSessionRepository: MetasploitSessionRepository =
        MetasploitSessionRepositoryImpl(rpcTransport, rpcTokenStore, sessionIoCoordinator)
    val termuxGateway: TermuxGateway = TermuxGatewayImpl(appContext, dispatcherProvider)
    val bootstrapCoordinator: BootstrapCoordinator = BootstrapCoordinatorImpl(
        termuxGateway = termuxGateway,
        metasploitRepository = metasploitConnectionRepository,
        installationStateRepository = installationStateRepository,
        secretStore = secretStore,
    )
}
