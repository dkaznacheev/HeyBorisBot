import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import java.lang.System.currentTimeMillis
import java.util.*

object Utils {
    val TIMEZONE = DateTimeZone.forOffsetHours(3)
    const val MIN_PLAYERS = 2
    private val random = Random(currentTimeMillis())

    fun today(): DateTime {
        return DateTime.now(DateTimeZone.forOffsetHours(3)).withTimeAtStartOfDay()
    }

    // TODO get weights in SQL query
    fun weightedVote(players: List<Player>, nominations: List<Pair<Long, Long>>): Player {
        val weights = nominations.groupingBy { it.first }.eachCount().toMutableMap()

        for (key in weights.keys) {
            weights[key] = weights[key]!! + 1
        }

        for (player in players) {
            weights.putIfAbsent(player.userId, 1)
        }

        val maxBound = weights.values.sum()
        val stop = random.nextInt(maxBound)
        var v = 0
        for (player in players) {
            val added = weights[player.userId] ?: 1
            if (v + added > stop)
                return player
            v += added
        }
        return players.last()
    }

    fun parseUserNames(text: String): List<String> {
        return text.split(" ").filter { it.startsWith("@") }.map { it.substring(1) }
    }
}