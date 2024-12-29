import java.io.File;
import java.io.FileWriter;
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
		System.out.println("MC World shifter by Lioncat6 and Darkutom");
		int shiftAmount = -320; // Shift down by 320 blocks
		String myWorld = "C:\\Users\\main\\AppData\\Roaming\\.minecraft\\saves\\newbotw";

		System.out
				.println("Starting the process of shifting the Minecraft world down by " + shiftAmount + " blocks...");

		System.out.println("Shifting reigion data (1/2)");

		shiftChunks(myWorld, shiftAmount);

		System.out.println("Shifting entity data (2/2)");
		errorCount.set(0);
		processedChunks.set(0);
		shiftEntities(myWorld, shiftAmount);
	}

	/** 
	 * @param myWorld
	 * @param shiftAmount
	 */
	private static void shiftChunks(String myWorld, int shiftAmount) {
		File regionDir = new File(myWorld, "region");
		int totalChunks = 32 * 32 * regionDir.listFiles((dir, name) -> name.endsWith(".mca")).length;

		ForkJoinPool forkJoinPool = new ForkJoinPool();
		ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

		// Schedule a task to update the progress bar and active threads every 5 seconds
		scheduler.scheduleAtFixedRate(() -> {
			int activeThreads = forkJoinPool.getActiveThreadCount();
			int processed = processedChunks.get();
			double progress = (double) processed / totalChunks * 100;

			// Clear the previous line and print the progress bar and active threads
			System.out.print("\r\033[K"); // Clear the line
			System.out.printf("Chunk Progress: %.2f%% (%d/%d) | Active threads: %d", progress, processed, totalChunks,
					activeThreads);
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
		System.out.println("\nReigion shift completed. | " + errorCount + " erros");
	}

	private static void shiftEntities(String myWorld, int shiftAmount) {
		File regionDir = new File(myWorld, "entities");
		int totalChunks = 32 * 32 * regionDir.listFiles((dir, name) -> name.endsWith(".mca")).length;

		ForkJoinPool forkJoinPool = new ForkJoinPool();
		ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

		// Schedule a task to update the progress bar and active threads every 5 seconds
		scheduler.scheduleAtFixedRate(() -> {
			int activeThreads = forkJoinPool.getActiveThreadCount();
			int processed = processedChunks.get();
			double progress = (double) processed / totalChunks * 100;

			// Clear the previous line and print the progress bar and active threads
			System.out.print("\r\033[K"); // Clear the line
			System.out.printf("Entity Progress: %.2f%% (%d/%d) | Active threads: %d", progress, processed, totalChunks,
					activeThreads);
		}, 0, 2, TimeUnit.SECONDS);

		for (File regionFile : regionDir.listFiles((dir, name) -> name.endsWith(".mca"))) {
			forkJoinPool.submit(() -> {
				try {
					processEntityFile(regionFile, shiftAmount, totalChunks);
				} catch (IOException e) {
					System.out
							.println("Error processing entity file: " + regionFile.getName() + " - " + e.getMessage());
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
		System.out.println("\nEntity shift completed. | " + errorCount + " erros");
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

	private static void processEntityFile(File regionFile, int shiftAmount, int totalChunks) throws IOException {
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
									shiftEntitiesDown(chunkData, shiftAmount);
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
		// Shift the block ticking
		chunkData.getAsListTag("block_ticks").ifPresent(blockTicksTag -> {
			blockTicksTag.getAsCompoundTagList().ifPresent(blockTicks -> {
				if (!blockTicks.getValue().isEmpty()) {
					for (CompoundTag blockTickData : blockTicks.getValue()) {
						blockTickData.getAsIntTag("y").ifPresent(yTag -> {
							int newY = yTag.getValue() + shiftAmount;
							blockTickData.getValue().put("y", new IntTag("y", newY));
						});
					}
				}
			});
		});
		// Shift the entities (if for some reason, it exists in the region files [entity
		// data was moved to its own mca files])
		chunkData.getAsListTag("entities").ifPresent(entitiesTag -> {
			entitiesTag.getAsCompoundTagList().ifPresent(entities -> {
				for (CompoundTag entityData : entities.getValue()) {
					entityData.getAsListTag("Pos").ifPresent(posTag -> {
						posTag.getAsDoubleTagList().ifPresent(pos -> {
							if (pos.getValue().size() > 1) {
								double newY = pos.getValue().get(1).getValue() + shiftAmount;
								pos.getValue().set(1, new DoubleTag("", newY));
							}
						});
					});
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

	private static void shiftEntitiesDown(CompoundTag chunkData, int shiftAmount) {

	    // Shift the entities
	    chunkData.getAsListTag("Entities").ifPresent(entitiesTag -> {
	        entitiesTag.getAsCompoundTagList().ifPresent(entities -> {
	            for (CompoundTag entityData : entities.getValue()) {
	                // Shift the "Pos" tag
	                entityData.getAsListTag("Pos").ifPresent(posTag -> {
	                    posTag.getAsDoubleTagList().ifPresent(pos -> {
	                        if (pos.getValue().size() > 1) {
	                            double newY = pos.getValue().get(1).getValue() + shiftAmount;
	                            pos.getValue().set(1, new DoubleTag("", newY));
	                        }
	                    });
	                });

	                // Shift the "TileY" tag
	                entityData.getAsIntTag("TileY").ifPresent(tileYTag -> {
	                    int newY = tileYTag.getValue() + shiftAmount;
	                    entityData.getValue().put("TileY", new IntTag("TileY", newY));
	                });

	                // Shift the "Paper.Origin" tag
	                entityData.getAsListTag("Paper.Origin").ifPresent(originTag -> {
	                    originTag.getAsDoubleTagList().ifPresent(origin -> {
	                        if (origin.getValue().size() > 1) {
	                            double newY = origin.getValue().get(1).getValue() + shiftAmount;
	                            origin.getValue().set(1, new DoubleTag("", newY));
	                        }
	                    });
	                });
	            }
	        });
	    });
	}

}