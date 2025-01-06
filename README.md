# Minecraft World Shifter

Minecraft World Shifter is a Java program designed to shift an entire Minecraft world vertically by any number of blocks. This program processes each region file in the world and adjusts the Y-values of chunks, all blocks and entities will be affected.

## Features

- **Customizable Shift Amount**: Move the world up or down by any number of blocks.
- **Supports All Blocks and Entities**: Ensures that every block and entity is shifted without errors.
- **Region File Processing**: Works directly with Minecraft's region files to make adjustments efficiently.

## How It Works

The program:
1. Reads Minecraft world region files.
2. Iterates through each chunk in the region files.
3. Adjusts the Y-coordinate of blocks and entities based on the specified shift amount.
4. Saves the updated region files back to disk.
