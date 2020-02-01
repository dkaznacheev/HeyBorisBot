import me.ivmg.telegram.Bot
import me.ivmg.telegram.entities.Message
import me.ivmg.telegram.entities.User
import org.joda.time.DateTime

class BotContext private constructor(
    private val bot: Bot,
    val day: DateTime,
    val chatId: Long,
    val from: User,
    val name: String,
    val text: String) {

    fun sendToUser(text: String, notify: Boolean = false) {
        bot.sendMessage(from.id, text=text, disableNotification = !notify)
    }

    fun sendToChat(text: String, notify: Boolean = false) {
        bot.sendMessage(chatId, text=text, disableNotification = !notify)
    }

    companion object {
        fun of(bot: Bot, message: Message): BotContext? {
            val from = message.from?: return null
            val name = from.username ?: from.firstName
            val text = message.text ?: return null
            return BotContext(bot, Utils.today(), message.chat.id, from, name, text)
        }
    }
}