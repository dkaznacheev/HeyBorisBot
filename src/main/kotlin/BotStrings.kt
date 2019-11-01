import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import java.io.File
import java.io.FileNotFoundException

abstract class StringsHolder(stringsPath: String) {
    private val mappings: Map<String, String>

    init {
        val directory = File(stringsPath)
        if (!directory.exists() || !directory.isDirectory) {
            throw FileNotFoundException(stringsPath)
        }
        mappings = directory.walk()
            .filter { it.isFile }
            .map(::parseMapping)
            .toMap()
    }

    abstract fun parseMapping(file: File): Pair<String, String>

    fun get(stringName: String, vararg subs: String): String {
        return mappings[stringName]?.format(*subs)
            ?: throw FileNotFoundException(stringName)
    }
}

class BotStrings(stringsPath: String): StringsHolder(stringsPath){
    private val format = DateTimeFormat.forPattern("dd.MM.yyyy").withZone(Utils.TIMEZONE)

    override fun parseMapping(file: File): Pair<String, String> {
        return file.name to file.readText()
    }

    fun showStats(players: List<Player>): String {
        val rating = players.filter { it.ptimes > 0 }.mapIndexed { i, player ->
            "${i + 1}. ${player.userName}: ${player.ptimes} раз(а)"
        }.joinToString("\n")

        return get("all_stats", players.size.toString(), rating)
    }

    fun showPlayerStats(ptimes: Long?, lastWon: DateTime?, name: String): String {
        if (ptimes == null || lastWon == null)
            return get("never_won", name)
        return get("personal_stats", name, ptimes.toString(), format.print(lastWon))
    }

    fun showRegResult(name: String, success: Boolean): String {
        return if (success) {
            "$name, добро пожаловать в игру!"
        } else {
            "Ты уже участвуешь в игре!"
        }
    }

    fun showWinner(userName: String, firstTime: Boolean): String {
        return if (firstTime) {
            get("winner_initial", userName)
        } else {
            get("winner_repeated", userName)
        }
    }
}

class BotCommands(stringsPath: String): StringsHolder(stringsPath) {
    override fun parseMapping(file: File): Pair<String, String> {
        return file.name to file.readText().replace("\n", "")
    }
}
