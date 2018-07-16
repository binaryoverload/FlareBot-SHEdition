package stream.flarebot.flarebot.commands.commands.music;

import com.arsenarsen.lavaplayerbridge.PlayerManager;
import com.arsenarsen.lavaplayerbridge.player.Track;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import lavalink.client.io.Link;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import stream.flarebot.flarebot.Client;
import stream.flarebot.flarebot.FlareBot;
import stream.flarebot.flarebot.Getters;
import stream.flarebot.flarebot.commands.*;
import stream.flarebot.flarebot.objects.GuildWrapper;
import stream.flarebot.flarebot.permissions.Permission;
import stream.flarebot.flarebot.util.MessageUtils;
import stream.flarebot.flarebot.util.buttons.ButtonGroupConstants;
import stream.flarebot.flarebot.util.general.MusicUtils;
import stream.flarebot.flarebot.util.objects.ButtonGroup;
import stream.flarebot.flarebot.util.votes.VoteGroup;
import stream.flarebot.flarebot.util.votes.VoteUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SkipCommand implements Command {
    private static final UUID skipUUID = UUID.randomUUID();

    @Override
    public void onCommand(User sender, GuildWrapper guild, TextChannel channel, Message message, String[] args, Member member) {
        boolean songMessage = message.getAuthor().getIdLong() == Getters.getSelfUser().getIdLong();
        if (!Client.instance().getLink(guild.getGuildId()).getState().equals(Link.State.CONNECTED) ||
                Client.instance().getPlayer(guild.getGuildId()).getPlayingTrack() == null) {
            MessageUtils.sendAutoDeletedMessage(new MessageBuilder().append("I am not playing anything!").build(), TimeUnit.SECONDS.toMillis(5), channel);
            return;
        }
        if (member.getVoiceState().inVoiceChannel() && !channel.getGuild().getSelfMember().getVoiceState().getChannel()
                .getId()
                .equals(member.getVoiceState().getChannel().getId())
                && !getPermissions(channel).hasPermission(member, Permission.SKIP_FORCE)) {
            MessageUtils.sendAutoDeletedMessage(new MessageBuilder().append("You must be in the channel in order to skip songs!").build(), TimeUnit.SECONDS.toMillis(5), channel);
            return;
        }
        AudioTrack currentTrack = Client.instance().getPlayer(guild.getGuildId()).getPlayingTrack();
        if (args.length == 0 && currentTrack.getUserData().equals(sender.getId())) {
            MusicUtils.skip(guild.getGuildId());
            MessageUtils.sendAutoDeletedMessage(new MessageBuilder().append("Skipped your own song!").build(), TimeUnit.SECONDS.toMillis(5), channel);
            if (songMessage)
                SongCommand.updateSongMessage(sender, message, channel);
            return;
        }

        if (args.length != 1) {
            if (!channel.getGuild().getMember(sender).getVoiceState().inVoiceChannel() ||
                    channel.getGuild().getMember(sender).getVoiceState().getChannel().getIdLong() != channel.getGuild().getSelfMember().getVoiceState().getChannel().getIdLong()) {
                MessageUtils.sendWarningMessage("You cannot skip if you aren't listening to it!", channel);
                return;
            }
            if (VoteUtil.contains(skipUUID, guild.getGuild()))
                MessageUtils.sendWarningMessage("There is already a vote to skip current song! Vote with `{%}skip yes | no`", channel, sender);
            else {
                VoteGroup group = new VoteGroup("Skip current song", skipUUID);
                List<User> users = new ArrayList<>();
                for (Member inChannelMember : channel.getGuild().getSelfMember().getVoiceState().getChannel().getMembers()) {
                    if (channel.getGuild().getSelfMember().getUser().getIdLong() != inChannelMember.getUser().getIdLong()) {
                        users.add(inChannelMember.getUser());
                    }
                }
                group.limitUsers(users);
                VoteUtil.sendVoteMessage(skipUUID, (vote) -> {
                            if (vote.equals(VoteGroup.Vote.NONE) || vote.equals(VoteGroup.Vote.NO)) {
                                MessageUtils.sendAutoDeletedMessage(new MessageBuilder().append("Results are in: Keep!").build(), TimeUnit.SECONDS.toMillis(5),  channel);
                            } else {
                                MessageUtils.sendAutoDeletedMessage(new MessageBuilder().append("Skipping!").build(), TimeUnit.SECONDS.toMillis(5),  channel);
                                MusicUtils.skip(guild.getGuildId());
                                if (songMessage)
                                    SongCommand.updateSongMessage(sender, message, channel);
                            }
                        }, group, TimeUnit.MINUTES.toMillis(1), channel, sender, ButtonGroupConstants.VOTE_SKIP,
                        new ButtonGroup.Button("\u23ED", (owner, user, message1) -> {
                            if (getPermissions(channel).hasPermission(channel.getGuild().getMember(user), Permission.SKIP_FORCE)) {
                                MusicUtils.skip(guild.getGuildId());
                                if (songMessage) {
                                    SongCommand.updateSongMessage(user, message1, channel);
                                }
                                VoteUtil.remove(skipUUID, guild.getGuild());
                            } else {
                                channel.sendMessage("You are missing the permission `" + Permission.SKIP_FORCE + "` which is required for use of this button!")
                                        .queue();
                            }
                        }));
            }
        } else {
            if (args[0].equalsIgnoreCase("force")) {
                if (getPermissions(channel).hasPermission(member, Permission.SKIP_FORCE)) {
                    MusicUtils.skip(guild.getGuildId());
                    if (songMessage)
                        SongCommand.updateSongMessage(sender, message, channel);
                    VoteUtil.remove(skipUUID, guild.getGuild());
                } else {
                    channel.sendMessage("You are missing the permission `" + Permission.SKIP_FORCE + "` which is required for use of this command!")
                            .queue();
                }
                return;
            } else if (args[0].equalsIgnoreCase("cancel")) {

                if (getPermissions(channel).hasPermission(member, Permission.SKIP_CANCEL)) {
                    VoteUtil.remove(skipUUID, channel.getGuild());
                } else
                    channel.sendMessage("You are missing the permission `" + Permission.SKIP_CANCEL + "` which is required for use of this command!")
                            .queue();
                return;
            }
            if (!channel.getGuild().getMember(sender).getVoiceState().inVoiceChannel() ||
                    channel.getGuild().getMember(sender).getVoiceState().getChannel().getIdLong() != channel.getGuild().getSelfMember().getVoiceState().getChannel().getIdLong()) {
                MessageUtils.sendWarningMessage("You cannot vote to skip if you aren't listening to it!", channel);
                return;
            }
            VoteGroup.Vote vote = VoteGroup.Vote.parseVote(args[0]);
            if (vote != null) {
                if (!VoteUtil.contains(skipUUID, guild.getGuild()))
                    MessageUtils.sendWarningMessage("Their is no vote currently running!", channel, sender);
                else
                    VoteUtil.getVoteGroup(skipUUID, guild.getGuild()).addVote(vote, sender);
            } else
                MessageUtils.sendUsage(this, channel, sender, args);
        }
    }

    public static UUID getSkipUUID() {
        return skipUUID;
    }

    @Override
    public String getCommand() {
        return "skip";
    }

    @Override
    public String getDescription() {
        return "Starts a skip voting, or if one is happening, marks a vote. `skip YES|NO` to pass a vote. To force skip use `skip force`." +
                " You can also use `skip cancel` to cancel the current vote as an admin.";
    }

    @Override
    public String getUsage() {
        return "`{%}skip` - Starts a vote to skip the song.\n" +
                "`{%}skip yes|no` - Vote yes or no to skip the current song.\n" +
                "`{%}skip force` - Forces FlareBot to skip the current song.";
    }

    @Override
    public Permission getPermission() {
        return Permission.SKIP_COMMAND;
    }

    @Override
    public CommandType getType() {
        return CommandType.MUSIC;
    }
}
