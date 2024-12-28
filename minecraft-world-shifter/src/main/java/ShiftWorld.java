import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import de.piegames.nbt.ByteTag;
import de.piegames.nbt.CompoundTag;
import de.piegames.nbt.DoubleTag;
import de.piegames.nbt.ListTag;
import de.piegames.nbt.IntTag;
import de.piegames.nbt.regionfile.Chunk;
import de.piegames.nbt.regionfile.RegionFile;

// authors: Lioncat6
// Darkutom

public class ShiftWorld {
	private static AtomicInteger errorCount = new AtomicInteger(0);
	private static AtomicInteger processedChunks = new AtomicInteger(0);

	public static void main(String[] args) throws IOException {
		int shiftAmount = -320; // Shift down by 320 blocks
		String myWorld = "C:\\Users\\main\\AppData\\Roaming\\.minecraft\\saves\\newbotw";

		File regionDir = new File(myWorld, "region");
		int totalChunks = 32 * 32 * regionDir.listFiles((dir, name) -> name.endsWith(".mca")).length;

		System.out
				.println("Starting the process of shifting the Minecraft world down by " + shiftAmount + " blocks...");
		System.out.println("MC World shifter by Lioncat6 and Darkutom");
		System.out.println();

		ForkJoinPool forkJoinPool = new ForkJoinPool();
		ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

		// Schedule a task to update the progress bar and active threads every 5 seconds
		scheduler.scheduleAtFixedRate(() -> {
			int activeThreads = forkJoinPool.getActiveThreadCount();
			int processed = processedChunks.get();
			double progress = (double) processed / totalChunks * 100;

			// Clear the previous line and print the progress bar and active threads
			System.out.print("\r\033[K"); // Clear the line
			System.out.printf("Progress: %.2f%% (%d/%d) | Active threads: %d", progress, processed, totalChunks, activeThreads);
		}, 0, 2, TimeUnit.SECONDS);

		for (File regionFile : regionDir.listFiles((dir, name) -> name.endsWith(".mca"))) {
			forkJoinPool.submit(() -> {
				try {
					processRegionFile(regionFile, shiftAmount, totalChunks);
				} catch (IOException e) {
					System.out
							.println("Error processing region file: " + regionFile.getName() + " - " + e.getMessage());
				}
			});
		}

		forkJoinPool.shutdown();
		try {
			forkJoinPool.close();
			forkJoinPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		scheduler.shutdown();
		System.out.println("\nShifting process completed. | " + errorCount + "erros");
	}

	private static void processRegionFile(File regionFile, int shiftAmount, int totalChunks) throws IOException {
		HashMap<Integer, Chunk> changedChunks = new HashMap<>();
		try (RegionFile region = new RegionFile(regionFile.toPath())) {
			ForkJoinPool forkJoinPool = new ForkJoinPool();
			for (int x = 0; x < 32; x++) {
				for (int z = 0; z < 32; z++) {
					if (!region.hasChunk(x, z))
						continue;

					final int chunkX = x;
					final int chunkZ = z;
					forkJoinPool.submit(() -> {
						try {
							Chunk chunk = region.loadChunk(chunkX, chunkZ);
							if (chunk != null) {
								processedChunks.incrementAndGet();
								try {
									CompoundTag chunkData = chunk.readTag().getAsCompoundTag().orElseThrow(
											() -> new IllegalStateException("Chunk data is not a CompoundTag"));
									shiftChunkDown(chunkData, shiftAmount);
									chunk = new Chunk(chunkX, chunkZ, Chunk.getCurrentTimestamp(), chunkData,
											chunk.getCompression());
									int position = RegionFile.coordsToPosition(chunkX, chunkZ);
									synchronized (changedChunks) {
										changedChunks.put(position, chunk);
									}
								} catch (Exception e) {
									errorCount.incrementAndGet();
								}
							}
						} catch (IOException e) {
							errorCount.incrementAndGet();
						}
					});
				}
			}
			forkJoinPool.shutdown();
			try {
				forkJoinPool.close();
				forkJoinPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			region.writeChunks(changedChunks);
		}
	}

	private static void shiftChunkDown(CompoundTag chunkData, int shiftAmount) {
		// Shift the sections
		ListTag<CompoundTag> sections = chunkData.getAsListTag("sections")
				.orElseThrow(() -> new IllegalStateException("Sections data is missing")).getAsCompoundTagList()
				.orElseThrow(() -> new IllegalStateException("Sections data is not a ListTag"));
		for (CompoundTag sectionData : sections.getValue()) {
			byte yOffset = (byte) (sectionData.getByteValue("Y")
					.orElseThrow(() -> new IllegalStateException("Y value is missing")) + shiftAmount / 16);
			sectionData.getValue().put("Y", new ByteTag("Y", yOffset));
		}

		// Shift the entities
		chunkData.getAsListTag("entities").ifPresent(entitiesTag -> {
			entitiesTag.getAsCompoundTagList().ifPresent(entities -> {
				if (!entities.getValue().isEmpty()) {
					for (CompoundTag entityData : entities.getValue()) {
						entityData.getAsListTag("Pos").ifPresent(posTag -> {
							posTag.getAsDoubleTagList().ifPresent(pos -> {
								if (pos.getValue().size() > 1) {
									double newY = pos.getValue().get(1).getValue() + shiftAmount;
									pos.getValue().set(1, new DoubleTag("Pos", newY));
								}
							});
						});
					}
				}
			});
		});
		// Shift the block entities
		chunkData.getAsListTag("block_entities").ifPresent(blockEntitiesTag -> {
			blockEntitiesTag.getAsCompoundTagList().ifPresent(blockEntities -> {
				if (!blockEntities.getValue().isEmpty()) {
					for (CompoundTag blockEntityData : blockEntities.getValue()) {
						blockEntityData.getAsIntTag("y").ifPresent(yTag -> {
							int newY = yTag.getValue() + shiftAmount;
							blockEntityData.getValue().put("y", new IntTag("y", newY));
						});
					}
				}
			});
		});
	}
}