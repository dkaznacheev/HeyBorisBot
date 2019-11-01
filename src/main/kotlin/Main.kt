import me.ivmg.telegram.Bot
import me.ivmg.telegram.bot
import me.ivmg.telegram.dispatch
import me.ivmg.telegram.dispatcher.Dispatcher
import me.ivmg.telegram.entities.Message
import me.ivmg.telegram.dispatcher.handlers.CommandHandler
import java.io.File
import java.io.FileInputStream
import java.util.*

fun Dispatcher.command(
    command: String,
    body: (Bot, Message) -> Unit) {
    addHandler(CommandHandler(command) { bot, update ->
        update.message?.let { message -> body(bot, message) }
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
                command(commands.get("help"), ::help)
                command(commands.get("all_stats"), ::stats)
                command(commands.get("personal_stats"), ::playerStats)
                command(commands.get("register"), ::playerReg)
                command(commands.get("nominate"), ::nominate)
                command(commands.get("winner"), ::winner)
            }
        }
    }


    private fun help(bot: Bot, msg: Message) {
        bot.sendMessage(msg.chat.id, strings.get("help"))
    }

    private fun stats(bot: Bot, msg: Message) {
        val chatId = msg.chat.id
        val players = h.players(chatId).sortedBy { -it.ptimes }
        if (players.isEmpty()) {
            bot.sendMessage(chatId, text=strings.get("not_enough_players"))
            return
        }
        val text = strings.showStats(players)
        bot.sendMessage(chatId, text = text)
    }

    private fun playerStats(bot: Bot, msg: Message) {
        val chatId = msg.chat.id
        val from = msg.from ?: return
        val userId = from.id
        val name = from.username ?: from.firstName

        val player = h.userStats(chatId, userId)
        val lastWon = h.lastWon(chatId, userId)
        val text = strings.showPlayerStats(player?.ptimes, lastWon, name)
        bot.sendMessage(chatId, text = text)
    }

    private fun playerReg(bot: Bot, msg: Message) {
        val chatId = msg.chat.id
        val from = msg.from ?: return
        val name = from.username ?: from.firstName
        val success = h.regPlayer(chatId, from.id, name)
        bot.sendMessage(chatId, text = strings.showRegResult(name, success))
    }

    private fun winner(bot: Bot, msg: Message) {
        val day = Utils.today()
        val chatId = msg.chat.id

        val (winner, firstTime) = h.getTodayWinner(chatId, day)
        if (winner == null) {
            bot.sendMessage(chatId, text = strings.get("not_enough_players"))
        } else {
            bot.sendMessage(chatId, text = strings.showWinner(winner.userName, firstTime))
        }
    }

    private fun nominate(bot: Bot, msg: Message) {
        val chatId = msg.chat.id
        val from = msg.from?.id ?: return

        val nominatedUserNames = Utils.parseUserNames(msg.text ?: return)

        if (nominatedUserNames.isEmpty()) {
            bot.sendMessage(chatId, text = strings.get("no_users_selected"))
            return
        }
        if (nominatedUserNames.size > 1) {
            bot.sendMessage(chatId, text = strings.get("too_many_users_selected"))
            return
        }
        val name = nominatedUserNames.first()
        val players = h.players(chatId)
        if (!players.any { it.userId == from }) {
            bot.sendMessage(chatId, text = strings.get("not_playing"))
            return
        }
        val nominated = players.firstOrNull { it.userName == name}?.userId
        if (nominated == null) {
            bot.sendMessage(chatId, text = strings.get("no_user"))
            return
        }
        if (nominated == from) {
            bot.sendMessage(chatId, text = strings.get("self_nominated"))
            return
        }

        val day = Utils.today()
        val result = h.nominate(chatId, from, nominated, day)
        if (result) {
            bot.sendMessage(chatId, text = strings.get("nominated_success", name))
        }
        else {
            bot.sendMessage(chatId, text = strings.get("already_nominated"))
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