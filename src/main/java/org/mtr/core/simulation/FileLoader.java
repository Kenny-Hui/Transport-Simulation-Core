package org.mtr.core.simulation;

import it.unimi.dsi.fastutil.ints.IntIntImmutablePair;
import it.unimi.dsi.fastutil.objects.Object2IntAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;
import org.mtr.core.Main;
import org.mtr.core.data.SerializedDataBaseWithId;
import org.mtr.core.serializers.MessagePackReader;
import org.mtr.core.serializers.MessagePackWriter;

import java.io.InputStream;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Stream;

public class FileLoader<T extends SerializedDataBaseWithId> {

	public final String key;
	private final Set<T> dataSet;
	private final Path path;
	private final Object2IntAVLTreeMap<String> fileHashes = new Object2IntAVLTreeMap<>();

	public FileLoader(Set<T> dataSet, Function<MessagePackReader, T> getData, Path rootPath, String key) {
		this.key = key;
		this.dataSet = dataSet;
		path = rootPath.resolve(key);
		createDirectory(path);
		readMessagePackFromFile(getData);
	}

	public IntIntImmutablePair save(boolean useReducedHash) {
		final ObjectArrayList<T> dirtyData = new ObjectArrayList<>(dataSet);
		final ObjectImmutableList<ObjectArrayList<String>> checkFilesToDelete = createEmptyList256();
		fileHashes.keySet().forEach(fileName -> checkFilesToDelete.get(getParentInt(fileName)).add(fileName));

		final int filesWritten = writeDirtyDataToFile(checkFilesToDelete, dirtyData, SerializedDataBaseWithId::getHexId, useReducedHash);
		int filesDeleted = 0;

		for (final ObjectArrayList<String> checkFilesToDeleteForParent : checkFilesToDelete) {
			for (final String fileName : checkFilesToDeleteForParent) {
				try {
					if (Files.deleteIfExists(path.resolve(fileName))) {
						filesDeleted++;
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				fileHashes.removeInt(fileName);
			}
		}

		return new IntIntImmutablePair(filesWritten, filesDeleted);
	}

	private void readMessagePackFromFile(Function<MessagePackReader, T> getData) {
		final Object2ObjectArrayMap<String, Future<T>> futureDataMap = new Object2ObjectArrayMap<>();
		final ExecutorService executorService = Executors.newCachedThreadPool();

		try (final Stream<Path> pathStream = Files.list(path)) {
			pathStream.forEach(idFolder -> {
				try (final Stream<Path> folderStream = Files.list(idFolder)) {
					folderStream.forEach(idFile -> futureDataMap.put(combineAsPath(idFolder, idFile), executorService.submit(() -> {
						try (final InputStream inputStream = Files.newInputStream(idFile)) {
							try (final MessageUnpacker messageUnpacker = MessagePack.newDefaultUnpacker(inputStream)) {
								return getData.apply(new MessagePackReader(messageUnpacker));
							} catch (Exception e) {
								e.printStackTrace();
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
						return null;
					})));
				} catch (Exception e) {
					e.printStackTrace();
				}

				try {
					Files.deleteIfExists(idFolder);
					Main.LOGGER.info(String.format("Deleted empty folder: %s", idFolder));
				} catch (DirectoryNotEmptyException ignored) {
				} catch (Exception e) {
					e.printStackTrace();
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}

		futureDataMap.forEach((fileName, futureData) -> {
			try {
				final T data = futureData.get();
				if (data != null) {
					if (data.isValid()) {
						dataSet.add(data);
					} else {
						Main.LOGGER.info(String.format("Skipping invalid data: %s", data));
					}
					fileHashes.put(fileName, getHash(data, true));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	private int writeDirtyDataToFile(ObjectImmutableList<ObjectArrayList<String>> checkFilesToDelete, ObjectArrayList<T> dirtyData, Function<T, String> getFileName, boolean useReducedHash) {
		int filesWritten = 0;
		while (!dirtyData.isEmpty()) {
			final T data = dirtyData.remove(0);

			if (data != null && data.isValid()) {
				final String fileName = getFileName.apply(data);
				final String parentFolderName = getParent(fileName);
				final String parentAndFileName = combineAsPath(parentFolderName, fileName);
				final int hash = getHash(data, useReducedHash);

				if (!fileHashes.containsKey(parentAndFileName) || hash != fileHashes.getInt(parentAndFileName)) {
					createDirectory(path.resolve(parentFolderName));

					try (final MessagePacker messagePacker = MessagePack.newDefaultPacker(Files.newOutputStream(path.resolve(parentAndFileName), StandardOpenOption.CREATE))) {
						packMessage(messagePacker, data, useReducedHash);
					} catch (Exception e) {
						e.printStackTrace();
					}

					fileHashes.put(fileName, hash);
					filesWritten++;
				}

				checkFilesToDelete.get(getParentInt(fileName)).remove(parentAndFileName);
			}
		}

		return filesWritten;
	}

	private static String getParent(String fileName) {
		return fileName.substring(Math.max(0, fileName.length() - 2));
	}

	private static int getParentInt(String fileName) {
		try {
			return Integer.parseInt(getParent(fileName), 16);
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
	}

	private static ObjectImmutableList<ObjectArrayList<String>> createEmptyList256() {
		final ObjectArrayList<ObjectArrayList<String>> list = new ObjectArrayList<>();
		for (int i = 0; i <= 0xFF; i++) {
			list.add(new ObjectArrayList<>());
		}
		return new ObjectImmutableList<>(list);
	}

	private static String combineAsPath(Path parentFolderPath, Path filePath) {
		return combineAsPath(parentFolderPath.getFileName().toString(), filePath.getFileName().toString());
	}

	private static String combineAsPath(String parentFolderName, String fileName) {
		return String.format("%s/%s", parentFolderName, fileName);
	}

	private static int getHash(SerializedDataBaseWithId data, boolean useReducedHash) {
		int hash = 0;
		try (final MessageBufferPacker messageBufferPacker = MessagePack.newDefaultBufferPacker()) {
			packMessage(messageBufferPacker, data, useReducedHash);
			hash = Arrays.hashCode(messageBufferPacker.toByteArray());
		} catch (Exception e) {
			e.printStackTrace();
		}
		return hash;
	}

	private static void packMessage(MessagePacker messagePacker, SerializedDataBaseWithId data, boolean useReducedHash) {
		final MessagePackWriter messagePackWriter = new MessagePackWriter(messagePacker);
		if (useReducedHash) {
			data.serializeData(messagePackWriter);
		} else {
			data.serializeFullData(messagePackWriter);
		}
		messagePackWriter.serialize();
	}

	private static void createDirectory(Path path) {
		if (!Files.exists(path)) {
			try {
				Files.createDirectories(path);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
