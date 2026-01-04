package test1.party;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PartyManager {
    private final Map<Integer, Party> parties = new HashMap<>();
    private final Map<UUID, Integer> playerPartyMap = new HashMap<>();

    public boolean createParty(Player creator, int roomId) {
        if (parties.containsKey(roomId)) return false; // 이미 존재하는 방 번호
        if (playerPartyMap.containsKey(creator.getUniqueId())) return false; // 이미 파티에 속함

        Party newParty = new Party(roomId, creator.getUniqueId());
        parties.put(roomId, newParty);
        playerPartyMap.put(creator.getUniqueId(), roomId);
        return true;
    }

    public boolean joinParty(Player player, int roomId) {
        if (playerPartyMap.containsKey(player.getUniqueId())) return false; // 이미 파티에 속함
        Party party = parties.get(roomId);
        if (party == null) return false; // 방이 없음

        party.addMember(player.getUniqueId());
        playerPartyMap.put(player.getUniqueId(), roomId);
        return true;
    }

    public void leaveParty(Player player) {
        UUID uuid = player.getUniqueId();
        if (!playerPartyMap.containsKey(uuid)) return;

        int roomId = playerPartyMap.remove(uuid);
        Party party = parties.get(roomId);
        if (party != null) {
            party.removeMember(uuid);
            if (party.isEmpty()) {
                parties.remove(roomId);
            }
        }
    }

    public Party getParty(Player player) {
        Integer roomId = playerPartyMap.get(player.getUniqueId());
        return roomId != null ? parties.get(roomId) : null;
    }

    public void disbandParty(int roomId) {
        Party party = parties.get(roomId);
        if (party != null) {
            for (UUID member : party.getMembers()) {
                playerPartyMap.remove(member);
            }
            parties.remove(roomId);
        }
    }
}