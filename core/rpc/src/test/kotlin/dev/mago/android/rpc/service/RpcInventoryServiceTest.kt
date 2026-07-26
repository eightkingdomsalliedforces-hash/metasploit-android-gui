package dev.mago.android.rpc.service

import com.google.common.truth.Truth.assertThat
import dev.mago.android.common.AppResult
import dev.mago.android.model.rpc.RpcValue
import dev.mago.android.rpc.RpcMethod
import dev.mago.android.rpc.RpcTransport
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RpcInventoryServiceTest {
    @Test
    fun `hosts uses bounded workspace options in official argument order`() = runTest {
        val transport = FakeTransport(
            RpcValue.MapValue(
                mapOf(
                    "hosts" to RpcValue.ArrayValue(
                        listOf(
                            RpcValue.MapValue(
                                mapOf(
                                    "address" to RpcValue.StringValue("192.0.2.10"),
                                    "state" to RpcValue.StringValue("alive"),
                                    "future" to RpcValue.StringValue("kept"),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val service = RpcInventoryService(transport)

        val result = service.hosts("token", "default", 100, 0)

        val hosts = (result as AppResult.Success).value
        assertThat(hosts.single().address).isEqualTo("192.0.2.10")
        assertThat(hosts.single().extraFields).containsKey("future")
        assertThat(transport.lastMethod).isEqualTo(RpcMethod.DB_HOSTS)
        val options = (transport.lastArguments.single() as RpcValue.MapValue).value
        assertThat(options["workspace"]).isEqualTo(RpcValue.StringValue("default"))
        assertThat(options["limit"]).isEqualTo(RpcValue.IntValue(100))
        assertThat(options["offset"]).isEqualTo(RpcValue.IntValue(0))
    }

    @Test
    fun `workspace management uses official methods and one string argument`() = runTest {
        val currentTransport = FakeTransport(
            RpcValue.MapValue(
                mapOf(
                    "workspace" to RpcValue.StringValue("default"),
                    "workspace_id" to RpcValue.IntValue(1),
                    "future" to RpcValue.StringValue("kept"),
                ),
            ),
        )
        val current = (RpcInventoryService(currentTransport).currentWorkspace("token") as AppResult.Success).value
        assertThat(current.name).isEqualTo("default")
        assertThat(current.extraFields).containsKey("future")
        assertThat(currentTransport.lastMethod).isEqualTo(RpcMethod.DB_CURRENT_WORKSPACE)
        assertThat(currentTransport.lastArguments).isEmpty()

        val addTransport = FakeTransport(successResponse())
        assertThat(RpcInventoryService(addTransport).addWorkspace("token", "lab_01")).isInstanceOf(AppResult.Success::class.java)
        assertThat(addTransport.lastMethod).isEqualTo(RpcMethod.DB_ADD_WORKSPACE)
        assertThat(addTransport.lastArguments).containsExactly(RpcValue.StringValue("lab_01"))

        val setTransport = FakeTransport(successResponse())
        assertThat(RpcInventoryService(setTransport).setWorkspace("token", "lab_01")).isInstanceOf(AppResult.Success::class.java)
        assertThat(setTransport.lastMethod).isEqualTo(RpcMethod.DB_SET_WORKSPACE)
        assertThat(setTransport.lastArguments).containsExactly(RpcValue.StringValue("lab_01"))
    }

    @Test
    fun `workspace mutation fails closed when result is not success`() = runTest {
        val service = RpcInventoryService(
            FakeTransport(
                RpcValue.MapValue(mapOf("result" to RpcValue.StringValue("failed"))),
            ),
        )

        assertThat(service.addWorkspace("token", "lab")).isInstanceOf(AppResult.Failure::class.java)
    }

    @Test
    fun `workspaces services and vulnerabilities parse official fields`() = runTest {
        val workspacesService = RpcInventoryService(
            FakeTransport(
                RpcValue.MapValue(
                    mapOf(
                        "workspaces" to RpcValue.ArrayValue(
                            listOf(
                                RpcValue.MapValue(
                                    mapOf(
                                        "id" to RpcValue.IntValue(1),
                                        "name" to RpcValue.StringValue("default"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertThat((workspacesService.workspaces("token") as AppResult.Success).value.single().name)
            .isEqualTo("default")

        val servicesService = RpcInventoryService(
            FakeTransport(
                RpcValue.MapValue(
                    mapOf(
                        "services" to RpcValue.ArrayValue(
                            listOf(
                                RpcValue.MapValue(
                                    mapOf(
                                        "host" to RpcValue.StringValue("192.0.2.10"),
                                        "port" to RpcValue.IntValue(443),
                                        "proto" to RpcValue.StringValue("tcp"),
                                        "name" to RpcValue.StringValue("https"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertThat((servicesService.services("token", "default", 100, 0) as AppResult.Success).value.single().port)
            .isEqualTo(443)

        val vulnerabilitiesService = RpcInventoryService(
            FakeTransport(
                RpcValue.MapValue(
                    mapOf(
                        "vulns" to RpcValue.ArrayValue(
                            listOf(
                                RpcValue.MapValue(
                                    mapOf(
                                        "host" to RpcValue.StringValue("192.0.2.10"),
                                        "name" to RpcValue.StringValue("Example"),
                                        "refs" to RpcValue.StringValue("CVE-2026-0001,URL-example"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertThat(
            (vulnerabilitiesService.vulnerabilities("token", "default", 100, 0) as AppResult.Success)
                .value.single().references,
        ).containsExactly("CVE-2026-0001", "URL-example").inOrder()
    }

    private fun successResponse() = RpcValue.MapValue(mapOf("result" to RpcValue.StringValue("success")))

    private class FakeTransport(private val response: RpcValue) : RpcTransport {
        var lastMethod: RpcMethod? = null
        var lastArguments: List<RpcValue> = emptyList()

        override suspend fun call(
            method: RpcMethod,
            token: String?,
            arguments: List<RpcValue>,
        ): AppResult<RpcValue> {
            lastMethod = method
            lastArguments = arguments
            return AppResult.Success(response)
        }
    }
}
