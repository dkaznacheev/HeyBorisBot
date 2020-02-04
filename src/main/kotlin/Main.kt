import me.ivmg.telegram.bot
import me.ivmg.telegram.Bot
import me.ivmg.telegram.dispatch
import me.ivmg.telegram.dispatcher.Dispatcher
import me.ivmg.telegram.dispatcher.handlers.CommandHandler
import java.io.File
import java.io.FileInputStream
import java.util.*

fun Dispatcher.command(
    command: String,
    body: BotContext.() -> Unit) {
    addHandler(CommandHandler(command) { bot, update ->
        update.message?.let { message ->
            BotContext.of(bot, message)?.also { it.log(command) }?.let(body)
        }
    })
}

class HeyBorisBot(p: Properties) {
    private val h: PSQLiteOpenHelper
    private val strings: BotStrings
    private val commands: BotCommands
    private val bot: Bot

    init {
        val stringsPath = p.getProperty("strings") ?: error("No strings property!")
        strings = BotStrings(stringsPath)

        val commandsPath = p.getProperty("commands") ?: error("No commands property!")
        commands = BotCommands(commandsPath)

        val dbPath = p.getProperty("dbPath") ?: error("No database path")
        h = PSQLiteOpenHelper(dbPath)

        val telegramToken = File(p.getProperty("tokenFile")).readText()
        bot = bot {
            token = telegramToken
            dispatch {
                command(commands.get("help")) { help() }
                command(commands.get("all_stats")) { stats() }
                command(commands.get("personal_stats")) { playerStats() }
                command(commands.get("register")) { playerReg() }
                command(commands.get("nominate")) { nominate() }
                command(commands.get("winner")) { winner() }
            }
        }
    }

    private fun BotContext.help() {
        sendToUser(strings.get("help"))
    }

    private fun BotContext.stats() {
        val players = h.players(chatId).sortedBy { -it.ptimes }
        if (players.isEmpty()) {
            sendToUser(strings.get("not_enough_players"))
            return
        }
        sendToUser(strings.showStats(players))
    }

    private fun BotContext.playerStats() {
        val player = h.userStats(chatId, from.id)
        val lastWon = h.lastWon(chatId, from.id)
        val text = strings.showPlayerStats(player?.ptimes, lastWon, name)
        sendToUser(text)
    }

    private fun BotContext.playerReg() {
        val success = h.regPlayer(chatId, from.id, name)
        sendToUser(strings.showRegResult(name, success))
    }

    private fun BotContext.winner() {
        val (winner, firstTime) = h.getTodayWinner(chatId, day)
        if (winner == null) {
            sendToUser(strings.get("not_enough_players"))
        } else {
            sendToChat(strings.showWinner(winner.userName, firstTime), notify = true)
        }
    }

    private fun BotContext.nominate() {
        val nominatedUserNames = Utils.parseUserNames(text)

        if (nominatedUserNames.isEmpty()) {
            sendToUser(strings.get("no_users_selected"))
            return
        }
        if (nominatedUserNames.size > 1) {
            sendToUser(strings.get("too_many_users_selected"))
            return
        }
        val name = nominatedUserNames.first()
        val players = h.players(chatId)
        if (!players.any { it.userId == from.id }) {
            sendToUser(strings.get("not_playing"))
            return
        }
        val nominated = players.firstOrNull { it.userName == name}?.userId
        if (nominated == null) {
            sendToUser(strings.get("no_user"))
            return
        }
        if (nominated == from.id) {
            sendToUser(strings.get("self_nominated"))
            return
        }

        val day = Utils.today()
        val result = h.nominate(chatId, from.id, nominated, day)
        if (result) {
            sendToUser(strings.get("nominated_success", name))
        }
        else {
            sendToUser(strings.get("already_nominated"))
        }
    }

    fun startPolling() {
        bot.startPolling()
    }

    companion object {
        private const val PROPERTIES_PATH = "bot.properties"

        @JvmStatic
        fun main(args: Array<String>) {
            val p = Properties()
            p.load(FileInputStream(PROPERTIES_PATH))
            val bot = HeyBorisBot(p)
            bot.startPolling()
        }
    }
}