package stream.flarebot.flarebot.tasks;

import com.arsenarsen.lavaplayerbridge.player.Player;
import net.dv8tion.jda.core.audio.hooks.ConnectionStatus;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.VoiceChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stream.flarebot.flarebot.Client;
import stream.flarebot.flarebot.FlareBot;
import stream.flarebot.flarebot.Getters;
import stream.flarebot.flarebot.metrics.Metrics;
import stream.flarebot.flarebot.scheduler.FlareBotTask;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class VoiceChannelCleanup extends FlareBotTask {

    private static final AtomicBoolean ENABLED = new AtomicBoolean(true);

    private static final Logger logger = LoggerFactory.getLogger(VoiceChannelCleanup.class);
    public static final Map<Long, Long> VC_LAST_USED = new HashMap<>();
    private static final long CLEANUP_THRESHOLD = TimeUnit.MINUTES.toMillis(20);

    private static final int WEBSOCKET_THRESHOLD = 50;
    private static final AtomicInteger closedConnections = new AtomicInteger(0);

    public VoiceChannelCleanup(String taskName) {
        super(taskName, TimeUnit.MINUTES.toMillis(20), TimeUnit.MINUTES.toMillis(5));
    }

    @Override
    public void run() {
        if (!ENABLED.get()) {
            cancel();
            return;
        }

        cleanupVoiceChannels();
        try {
            Thread.sleep(60_000); // WebSocket is 120 events in 60 second period. Let's wait till it's reset before possibly sending a large amount more
        } catch (InterruptedException e) {
            logger.error("Failed to sleep till the websocket reset", e);
            return;
        }
        cleanupPlayers();
    }

    private void cleanupVoiceChannels() {
        closedConnections.set(0);
        logger.info("Checking for guilds with an inactive voice channel");
        final AtomicInteger totalGuilds = new AtomicInteger(0);
        final AtomicInteger totalVcs = new AtomicInteger(0);
        final AtomicInteger killedVcs = new AtomicInteger(0);

        Getters.getGuildCache().forEach(guild -> {
            try {
                totalGuilds.incrementAndGet();

                if (guild != null && guild.getSelfMember() != null && guild.getSelfMember().getVoiceState() != null
                        && guild.getSelfMember().getVoiceState().getChannel() != null) {
                    totalVcs.incrementAndGet();

                    VoiceChannel vc = guild.getSelfMember().getVoiceState().getChannel();

                    if (getHumansInChannel(vc) == 0) {
                        killedVcs.incrementAndGet();
                        cleanup(guild, Client.instance().getMusicManager().getPlayer(guild.getId()), vc.getIdLong());
                    } else if (isPlayingMusic(vc)) {
                        VC_LAST_USED.put(vc.getIdLong(), System.currentTimeMillis());
                    } else {
                        if (!VC_LAST_USED.containsKey(vc.getIdLong())) {
                            VC_LAST_USED.put(vc.getIdLong(), System.currentTimeMillis());
                            return;
                        }

                        long lastUsed = VC_LAST_USED.get(vc.getIdLong());

                        if (System.currentTimeMillis() - lastUsed >= CLEANUP_THRESHOLD) {
                            killedVcs.incrementAndGet();
                            cleanup(guild, Client.instance().getMusicManager().getPlayer(guild.getId()), vc.getIdLong());
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to check {} for inactive voice connection!", guild.getIdLong(), e);
            }
        });

        logger.info("Checked {} guilds for inactive voice channels.", totalGuilds.get());
        logger.info("Killed {} out of {} voice connections!", killedVcs.get(), totalVcs.get());
        Metrics.voiceChannelsCleanedUp.inc(killedVcs.get());
    }

    private void cleanupPlayers() {
        closedConnections.set(0);
        logger.info("Checking for players which are inactive");
        final AtomicInteger totalPlayers = new AtomicInteger(0);
        final AtomicInteger killedPlayers = new AtomicInteger(0);
        final AtomicInteger songsCleared = new AtomicInteger(0);

        Client.instance().getMusicManager().getPlayers().forEach(player -> {
            try {
                totalPlayers.incrementAndGet();
                long guildId = Long.parseLong(player.getGuildId());

                Guild g = Getters.getGuildById(guildId);
                if (g == null) {
                    cleanup(null, player, guildId);

                    songsCleared.set(songsCleared.addAndGet(player.getPlaylist().size()));
                    killedPlayers.incrementAndGet();
                    return;
                }

                if (g.getSelfMember().getVoiceState() == null ||
                        g.getSelfMember().getVoiceState().getChannel() == null) {
                    if (!VC_LAST_USED.containsKey(guildId)) {
                        VC_LAST_USED.put(guildId, System.currentTimeMillis());
                        return;
                    }

                    if (System.currentTimeMillis() - VC_LAST_USED.get(guildId) >= CLEANUP_THRESHOLD) {
                        killedPlayers.incrementAndGet();
                        songsCleared.set(songsCleared.addAndGet(player.getPlaylist().size()));
                        cleanup(g, player, guildId);
                        return;
                    }
                    return;
                }
                VoiceChannel vc = g.getSelfMember().getVoiceState().getChannel();
                if (!isPlayingMusic(vc)) {
                    if (!VC_LAST_USED.containsKey(guildId)) {
                        VC_LAST_USED.put(guildId, System.currentTimeMillis());
                        return;
                    }

                    if (System.currentTimeMillis() - VC_LAST_USED.get(guildId) >= CLEANUP_THRESHOLD) {
                        killedPlayers.incrementAndGet();
                        songsCleared.set(songsCleared.addAndGet(player.getPlaylist().size()));
                        cleanup(g, player, guildId);
                    }
                } else {
                    VC_LAST_USED.remove(guildId);
                }
            } catch (Exception e) {
                logger.error("Failed to check {} for inactive player!", player.getGuildId(), e);
            }
        });

        logger.info("Checked {} players for inactivity.", totalPlayers.get());
        logger.info("Killed {} out of {} players!", killedPlayers.get(), totalPlayers.get());
        logger.info("Cleaned {} songs from players", songsCleared.get());
        Metrics.playersCleanedUp.inc(killedPlayers.get());
    }

    private void cleanup(Guild guild, Player player, long id) {
        if (guild != null && guild.getSelfMember().getVoiceState() != null
                && guild.getSelfMember().getVoiceState().getChannel() != null
                && guild.getAudioManager().isConnected()
                && guild.getAudioManager().getConnectionStatus() == ConnectionStatus.CONNECTED
                && closedConnections.get() < WEBSOCKET_THRESHOLD) { // Cut off at threshold in-case we send too many
            guild.getAudioManager().closeAudioConnection();
            closedConnections.incrementAndGet();
        }

        Client.instance().getMusicManager().deletePlayer(player);
        VC_LAST_USED.remove(id);
    }

    private int getHumansInChannel(VoiceChannel channel) {
        int i = 0;
        for (Member m : channel.getMembers())
            if (!m.getUser().isBot())
                i++;
        return i;
    }

    private boolean isPlayingMusic(VoiceChannel vc) {
        Player player = Client.instance().getMusicManager().getPlayer(vc.getGuild().getId());
        return player != null && !player.getPaused() && player.getPlayingTrack() != null;
    }
}
