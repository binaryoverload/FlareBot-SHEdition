package stream.flarebot.flarebot.util;

import net.dv8tion.jda.core.requests.RestAction;
import org.eclipse.jetty.util.ConcurrentHashSet;
import stream.flarebot.flarebot.commands.Command;
import stream.flarebot.flarebot.commands.moderation.PruneCommand;
import stream.flarebot.flarebot.objects.RestActionWrapper;

import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ConfirmLib {

    private static final ExpiringMap<String, Set<RestActionWrapper>> confirmMap = new ExpiringMap<>(TimeUnit.MINUTES.toMillis(1));

    public static void pushAction(String userID, RestActionWrapper action) {
        if (confirmMap.containsKey(userID)) {
            Set<RestActionWrapper> actions = confirmMap.get(userID);
            if (actions.stream().filter(wrapper -> wrapper.getOrigin().equals(action.getOrigin())).count() == 0) {
                actions.add(action);
            }
        } else {
            Set<RestActionWrapper> actions = new ConcurrentHashSet<>();
            actions.add(action);
            confirmMap.put(userID, actions);
        }
    }

    public static RestAction get(String userID, Class<? extends Command> command) {
        if (confirmMap.containsKey(userID)) {
            Set<RestActionWrapper> wrappers = confirmMap.get(userID);
            return ((RestActionWrapper) wrappers.stream().filter(wrapper -> wrapper.getOrigin().equals(command)).toArray()[0]).getAction();
        }
        return null;
    }

    public static void removeUser(String userID) {
        if(confirmMap.containsKey(userID)) {
            confirmMap.remove(userID);
        }
    }

    public static boolean checkExists(String userID, Class<? extends Command> command) {
        if (confirmMap.containsKey(userID)) {
            Set<RestActionWrapper> wrappers = confirmMap.get(userID);
            return wrappers.stream().filter(wrapper -> wrapper.getOrigin().equals(command)).count() > 0;
        }
        return false;
    }

    public static void clearConfirmMap() {
        ConfirmLib.clearConfirmMap(false);
    }

    public static void clearConfirmMap(boolean force) {
        confirmMap.purge(force);
    }


    public static void remove(String id, Class<? extends PruneCommand> command) {
        if (checkExists(id, command)) {
            confirmMap.get(id).removeIf(restActionWrapper -> restActionWrapper.getOrigin().equals(command));
        }
    }

}
