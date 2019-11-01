import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

// TODO This is terrible and should be normalized
class PSQLiteOpenHelper(dbPath: String) {
    init {
        Database.connect("jdbc:sqlite:$dbPath", driver = "org.sqlite.JDBC")
    }

    private fun toPlayer(it: ResultRow) =
        Player(it[Players.userId], it[Players.userName], it[Players.ptimes])

    fun players(chatId: Long): List<Player> {
        return transaction {
            Players.select { Players.chatId.eq(chatId) }
                .map { toPlayer(it) }
                .toList()
        }
    }

    // TODO Figure out JOIN and GROUPBY in Exposed
    fun getTodayWinner(chatId: Long, day: DateTime): Pair<Player?, Boolean> {
        return transaction {
            val todayWinner = Winners.select {
                Winners.chatId.eq(chatId).and(Winners.day.eq(day))
            }
            if (!todayWinner.empty()) {
                val winnerId = todayWinner.first()[Winners.userId]
                val winnerPlayer = Players.select {
                    Players.chatId.eq(chatId).and(Players.userId.eq(winnerId))
                }.first()
                return@transaction toPlayer(winnerPlayer) to false
            }

            val prevDay = day.minusDays(1)
            val players = Players.select { Players.chatId.eq(chatId) }.map { toPlayer(it)}
            if (players.size < Utils.MIN_PLAYERS) {
                return@transaction null to false
            }
            val nominations = Nominations.select {
                Nominations.chatId.eq(chatId).and(Nominations.day.eq(prevDay))
            }.map { it[Nominations.nominatedId] to it[Nominations.userId] }

            val winner: Player = Utils.weightedVote(players, nominations)
            val winnerId = winner.userId

            Winners.insert {
                it[Winners.day] = day
                it[Winners.chatId] = chatId
                it[Winners.userId] = winnerId
            }

            Players.update({Players.chatId.eq(chatId).and(Players.userId.eq(winnerId))}) {
                with(SqlExpressionBuilder) {
                    it.update(Players.ptimes, Players.ptimes + 1)
                }
            }
            return@transaction winner to true // ptimes actually 1 less in returned
        }
    }

    fun nominate(chatId: Long, userId: Long, nominatedId: Long, day: DateTime): Boolean {
        return transaction {
            val exists = Nominations.select {
                (Nominations.chatId eq chatId) and (Nominations.userId eq userId) and (Nominations.day eq day)
            }.empty().not()
            if (exists) {
                return@transaction false
            }
            Nominations.insert {
                it[Nominations.chatId] = chatId
                it[Nominations.userId] = userId
                it[Nominations.nominatedId] = nominatedId
                it[Nominations.day] = day
            }
            println("nominated $nominatedId by $userId in $chatId")
            true
        }
    }

    fun regPlayer(chatId: Long, userId: Long, userName: String): Boolean {
        return transaction {
            val exists = Players.select {
                Players.chatId.eq(chatId) and Players.userId.eq(userId)
            }.empty().not()
            if (exists) {
                return@transaction false
            }
            Players.insert {
                it[Players.chatId] = chatId
                it[Players.userId] = userId
                it[Players.userName] = userName
                it[Players.ptimes] = 0
            }
            true
        }
    }

    fun userStats(chatId: Long, userId: Long): Player? {
        return transaction {
            Players.select {
                Players.chatId.eq(chatId) and Players.userId.eq(userId)
            }.limit(1).map { toPlayer(it) }.firstOrNull()
        }
    }

    fun lastWon(chatId: Long, userId: Long): DateTime? {
        return transaction {
            val lastDay = Winners.slice(Winners.day.max())
                .select { Winners.chatId.eq(chatId).and(Winners.userId.eq(userId)) }
                .groupBy(Winners.userId)
            return@transaction lastDay.firstOrNull()?.get(lastDay.set.fields.first()) as? DateTime
        }
    }
}

object Players : Table()  {
    val chatId: Column<Long> = long("chat_id")
    val userId: Column<Long> = long("user_id")
    val userName: Column<String> = varchar("user_name", 50)
    val ptimes: Column<Long> = long("ptimes")
}

object Nominations: Table() {
    val day: Column<DateTime> = datetime("day")
    val chatId: Column<Long> = long("chat_id")
    val userId: Column<Long> = long("user_id")
    val nominatedId: Column<Long> = long("nominated_id")
}

object Winners: Table() {
    val day: Column<DateTime> = datetime("day")
    val chatId: Column<Long> = long("chat_id")
    val userId: Column<Long> = long("user_id")
}

data class Player(
    val userId: Long,
    val userName: String,
    val ptimes: Long)