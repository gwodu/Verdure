# Download Model Here

This directory should contain the Llama 3.2 1B Instruct model for on-device AI inference.

## Required File

**Filename:** `Llama-3.2-1B-Instruct-Q4_K_M.gguf`
**Size:** ~700 MB
**Format:** GGUF (quantized, 4-bit)

## Download Instructions

### Option 1: Direct Download (wget)

```bash
cd VerdureApp/app/src/main/assets/models/
wget https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf
```

### Option 2: Browser Download

1. Visit: https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF
2. Click on "Files and versions"
3. Download: `Llama-3.2-1B-Instruct-Q4_K_M.gguf`
4. Move the file to this directory

### Option 3: HuggingFace CLI

```bash
huggingface-cli download bartowski/Llama-3.2-1B-Instruct-GGUF Llama-3.2-1B-Instruct-Q4_K_M.gguf --local-dir .
```

## Verify Download

After downloading, verify the file is in place:

```bash
ls -lh VerdureApp/app/src/main/assets/models/
# Should show: Llama-3.2-1B-Instruct-Q4_K_M.gguf (~700 MB)
```

## Git Ignore

This model file is too large for git and is listed in `.gitignore`. Each developer must download it separately.

## Alternative Models

You can also try other quantization levels (trade-off between size and quality):

- **Q3_K_M** (~550 MB): Smaller, slightly lower quality
- **Q5_K_M** (~900 MB): Larger, slightly better quality
- **Q8_0** (~1.3 GB): Even better quality, but slower

To use a different model, update `LlamaCppEngine.kt`:
```kotlin
const val MODEL_FILENAME = "your-model-name.gguf"
```
