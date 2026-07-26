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
