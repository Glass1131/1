package test1.party;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Party {
    private final int id;
    private UUID admin;
    private final Set<UUID> members = new HashSet<>();

    public Party(int id, UUID creator) {
        this.id = id;
        this.admin = creator;
        this.members.add(creator);
    }

    public int getId() {
        return id;
    }

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    public void addMember(UUID uuid) {
        members.add(uuid);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
        // 만약 관리자가 나갔는데 멤버가 남아있다면 다른 사람에게 권한 위임
        if (uuid.equals(admin) && !members.isEmpty()) {
            admin = members.iterator().next();
            Player newAdmin = Bukkit.getPlayer(admin);
            if (newAdmin != null) {
                newAdmin.sendMessage("§a이전 관리자가 파티를 떠나 방장이 되었습니다!");
            }
        }
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }

    public boolean isAdmin(UUID uuid) {
        return admin.equals(uuid);
    }
}