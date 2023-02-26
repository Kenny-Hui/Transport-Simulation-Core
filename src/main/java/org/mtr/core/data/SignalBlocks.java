package org.mtr.core.data;

import org.msgpack.core.MessagePacker;
import org.mtr.core.tools.DyeColor;

import java.io.IOException;
import java.util.*;

public class SignalBlocks {

	private final Map<UUID, Set<SignalBlock>> railToSignalBlocks = new HashMap<>();
	public final List<SignalBlock> signalBlocks = new ArrayList<>();

	public long add(long id, DyeColor color, UUID rail) {
		final List<SignalBlock> connectedSignalBlocks = new ArrayList<>();
		signalBlocks.forEach(signalBlock -> {
			if (signalBlock.color == color && signalBlock.isConnected(rail)) {
				connectedSignalBlocks.add(signalBlock);
			}
		});

		if (connectedSignalBlocks.isEmpty()) {
			final SignalBlock newSignalBlock = new SignalBlock(id, color, rail);
			signalBlocks.add(newSignalBlock);
			writeCache();
			return newSignalBlock.id;
		} else {
			Collections.sort(connectedSignalBlocks);
			final SignalBlock firstSignalBlock = connectedSignalBlocks.remove(0);
			firstSignalBlock.rails.add(rail);
			connectedSignalBlocks.forEach(signalBlock -> firstSignalBlock.rails.addAll(signalBlock.rails));
			signalBlocks.removeIf(connectedSignalBlocks::contains);
			writeCache();
			return 0;
		}
	}

	public long remove(long id, DyeColor color, UUID rail) {
		SignalBlock connectedSignalBlock = null;
		for (final SignalBlock signalBlock : signalBlocks) {
			if (signalBlock.color == color && signalBlock.isConnected(rail)) {
				connectedSignalBlock = signalBlock;
				break;
			}
		}

		if (connectedSignalBlock != null) {
			signalBlocks.remove(connectedSignalBlock);
			connectedSignalBlock.rails.remove(rail);

			if (!connectedSignalBlock.rails.isEmpty()) {
				final List<UUID> rails = new ArrayList<>(connectedSignalBlock.rails);
				Collections.sort(rails);
				add(connectedSignalBlock.id, color, rails.remove(0));

				long returnId = 0;
				for (final UUID existingRail : rails) {
					final long newId = add(id, color, existingRail);
					if (newId != connectedSignalBlock.id) {
						returnId = newId;
					}
				}

				writeCache();
				return returnId;
			}
		}

		writeCache();
		return 0;
	}

	public void occupy(UUID currentRail, List<Map<UUID, Long>> trainPositions, long trainId) {
		if (trainPositions.size() < 2) {
			return;
		}

		final Set<UUID> railsToAdd = new HashSet<>();
		railsToAdd.add(currentRail);

		if (railToSignalBlocks.containsKey(currentRail)) {
			railToSignalBlocks.get(currentRail).forEach(signalBlock -> {
				railsToAdd.addAll(signalBlock.rails);
				signalBlock.occupied = 2;
			});
		}

		for (final Map<UUID, Long> trainPositionsMap : trainPositions) {
			if (railsToAdd.stream().anyMatch(rail -> trainPositionsMap.containsKey(rail) && trainPositionsMap.get(rail) != trainId)) {
				return;
			}
		}

		railsToAdd.forEach(rail -> trainPositions.get(1).put(rail, trainId));
	}

	public void resetOccupied() {
		signalBlocks.forEach(signalBlock -> {
			if (signalBlock.isOccupied()) {
				signalBlock.occupied--;
			}
		});
	}

	public List<SignalBlock> getSignalBlocksAtTrack(UUID rail) {
		if (railToSignalBlocks.containsKey(rail)) {
			final List<SignalBlock> matchingSignalBlocks = new ArrayList<>(railToSignalBlocks.get(rail));
			matchingSignalBlocks.sort(Comparator.comparingInt(signalBlock -> signalBlock.color.ordinal()));
			return matchingSignalBlocks;
		} else {
			return new ArrayList<>();
		}
	}

	public boolean isOccupied(UUID rail) {
		if (railToSignalBlocks.containsKey(rail)) {
			return railToSignalBlocks.get(rail).stream().anyMatch(SignalBlock::isOccupied);
		} else {
			return false;
		}
	}

	public void getSignalBlockStatus(Map<Long, Boolean> signalBlockStatus, UUID rail) {
		if (railToSignalBlocks.containsKey(rail)) {
			railToSignalBlocks.get(rail).forEach(signalBlock -> signalBlockStatus.put(signalBlock.id, signalBlock.isOccupied()));
		}
	}

	public void writeSignalBlockStatus(Map<Long, Boolean> signalBlockStatus) {
		signalBlockStatus.forEach((id, occupied) -> signalBlocks.forEach(signalBlock -> {
			if (signalBlock.id == id) {
				signalBlock.occupied = occupied ? 2 : 0;
			}
		}));
	}

	public void writeCache() {
		railToSignalBlocks.clear();
		signalBlocks.forEach(signalBlock -> signalBlock.rails.forEach(rail -> {
			if (!railToSignalBlocks.containsKey(rail)) {
				railToSignalBlocks.put(rail, new HashSet<>());
			}
			railToSignalBlocks.get(rail).add(signalBlock);
		}));
	}

	public static class SignalBlock extends NameColorDataBase {

		public final DyeColor color;
		private final Set<UUID> rails = new HashSet<>();
		private int occupied = 0;

		private static final String KEY_COLOR = "color";
		private static final String KEY_RAILS = "rails";

		private SignalBlock(long id, DyeColor color, UUID rail) {
			super(id);
			this.color = color;
			rails.add(rail);
		}

		public SignalBlock(MessagePackHelper messagePackHelper) {
			super(messagePackHelper);
			DyeColor savedColor;
			try {
				savedColor = DyeColor.values()[messagePackHelper.getInt(KEY_COLOR, 0)];
			} catch (Exception e) {
				e.printStackTrace();
				savedColor = DyeColor.RED;
			}
			color = savedColor;

			messagePackHelper.iterateArrayValue(KEY_RAILS, value -> rails.add(UUID.fromString(value.asStringValue().asString())));
		}

		@Override
		public void toMessagePack(MessagePacker messagePacker) throws IOException {
			super.toMessagePack(messagePacker);

			messagePacker.packString(KEY_COLOR).packInt(color.ordinal());
			messagePacker.packString(KEY_RAILS).packArrayHeader(rails.size());
			for (final UUID rail : rails) {
				messagePacker.packString(rail.toString());
			}
		}

		@Override
		public int messagePackLength() {
			return super.messagePackLength() + 2;
		}

		@Override
		protected boolean hasTransportMode() {
			return false;
		}

		public boolean isOccupied() {
			return occupied > 0;
		}

		private boolean isConnected(UUID checkRail) {
			final long checkPos1 = checkRail.getLeastSignificantBits();
			final long checkPos2 = checkRail.getMostSignificantBits();
			return rails.stream().anyMatch(rail -> {
				final long pos1 = rail.getLeastSignificantBits();
				final long pos2 = rail.getMostSignificantBits();
				return checkPos1 == pos1 || checkPos1 == pos2 || checkPos2 == pos1 || checkPos2 == pos2;
			});
		}

		@Override
		public int compareTo(NameColorDataBase compare) {
			return Long.compare(id, compare.id);
		}
	}
}
