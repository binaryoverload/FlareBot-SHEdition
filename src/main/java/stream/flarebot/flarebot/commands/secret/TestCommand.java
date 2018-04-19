package stream.flarebot.flarebot.commands.secret;

import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import org.slf4j.MDC;
import stream.flarebot.flarebot.commands.CommandType;
import stream.flarebot.flarebot.commands.AdminCommand;
import stream.flarebot.flarebot.objects.GuildWrapper;

public class TestCommand implements AdminCommand {

    @Override
    public void onCommand(User sender, GuildWrapper guild, TextChannel channel, Message message, String[] args, Member member) {
        channel.sendMessage(MDC.getCopyOfContextMap().keySet().toString()).queue();
    }

    @Override
    public String getCommand() {
        return "test";
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public String getUsage() {
        return "{%}test";
    }

    @Override
    public CommandType getType() {
        return CommandType.RANDOM;
    }
}
