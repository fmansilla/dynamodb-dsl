package ar.ferman.dynamodb.dsl.async

import ar.ferman.dynamodb.dsl.AttributeType
import ar.ferman.dynamodb.dsl.DynamoDbForTests
import ar.ferman.dynamodb.dsl.TableDefinition
import ar.ferman.dynamodb.dsl.TableKeyAttribute
import ar.ferman.dynamodb.dsl.example.ranking.UserRanking
import ar.ferman.dynamodb.dsl.example.ranking.UserRankingItemMapper
import ar.ferman.dynamodb.dsl.example.ranking.UserRankingTable
import ar.ferman.dynamodb.dsl.utils.KGenericContainer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.BDDAssertions.then
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

@Testcontainers
class TableTest {

    companion object {
        @Container
        @JvmField
        val dynamoDbContainer: KGenericContainer = DynamoDbForTests.createContainer()


        private const val USERNAME_1 = "username_1"
        private const val USERNAME_2 = "username_2"
        private const val USERNAME_3 = "username_3"
    }

    private lateinit var dynamoDbClient: DynamoDbAsyncClient
    private lateinit var table: Table
    private lateinit var itemMapper: UserRankingItemMapper

    @BeforeEach
    internal fun setUp() = runBlocking<Unit> {
        dynamoDbClient = DynamoDbForTests.createAsyncClient(dynamoDbContainer)
        table = Table(
            dynamoDbClient,
            TableDefinition(
                name = UserRankingTable.TableName,
                hashKey = TableKeyAttribute(UserRankingTable.UserId, AttributeType.STRING)
            )
        )
        itemMapper = UserRankingItemMapper()

        table.createIfNotExist()
    }

    @Test
    fun `query for non existent elements returns empty`() = runBlocking<Unit> {
        val result = table.query<UserRanking> {
            attributes(
                UserRankingTable.UserId,
                UserRankingTable.Score
            )
            mappingItems(itemMapper::fromItem)
            where {
                UserRankingTable.UserId eq USERNAME_1
            }
        }.toList()

        then(result).isEmpty()
    }

    @Test
    fun `query for single existent element returns it`() = runBlocking<Unit> {
        table.put(UserRanking(USERNAME_1, 5), itemMapper::toItem)
        table.put(UserRanking(USERNAME_2, 10), itemMapper::toItem)
        table.put(UserRanking(USERNAME_3, 15), itemMapper::toItem)

        val result = table.query<UserRanking> {
            attributes(
                UserRankingTable.UserId,
                UserRankingTable.Score
            )
            mappingItems(itemMapper::fromItem)
            where {
                UserRankingTable.UserId eq USERNAME_1
            }
        }.toList()

        then(result).containsExactly(UserRanking(USERNAME_1, 5))
    }


    @Test
    fun `scan empty table does not return items`() = runBlocking<Unit> {
        val result = table.scan<UserRanking> {
            attributes(
                UserRankingTable.UserId,
                UserRankingTable.Score
            )
            mappingItems(itemMapper::fromItem)
        }.toList()

        then(result).isEmpty()
    }

    @Test
    fun `scan non empty table return all items`() = runBlocking<Unit> {
        table.put(UserRanking(USERNAME_1, 5), itemMapper::toItem)
        table.put(UserRanking(USERNAME_2, 10), itemMapper::toItem)
        table.put(UserRanking(USERNAME_3, 15), itemMapper::toItem)

        val result = table.scan<UserRanking> {
            attributes(
                UserRankingTable.UserId,
                UserRankingTable.Score
            )
            mappingItems(itemMapper::fromItem)
        }.toList()

        then(result).containsExactlyInAnyOrder(
            UserRanking(USERNAME_1, 5),
            UserRanking(USERNAME_2, 10),
            UserRanking(USERNAME_3, 15)
        )
    }

    @Test
    fun `update only some attributes`() = runBlocking<Unit> {
        table.put(UserRanking(USERNAME_1, 5), itemMapper::toItem)
        table.update {
            set(UserRankingTable.Score, 10)
            where {
                UserRankingTable.UserId eq USERNAME_1
            }
        }

        val result = table.scan<UserRanking> {
            attributes(
                UserRankingTable.UserId,
                UserRankingTable.Score
            )
            mappingItems(itemMapper::fromItem)
        }.toList()

        then(result).containsExactlyInAnyOrder(UserRanking(USERNAME_1, 10))
    }
}
