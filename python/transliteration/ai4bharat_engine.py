import torch
from ai4bharat.transliteration import XlitEngine

# Load model (example Gujarati)
engine = XlitEngine("gu")

# Grab internal PyTorch model
model = engine.model

# Convert to TorchScript
example = torch.randint(0, 100, (1, 20))  # fake input, adjust per tokenizer
traced = torch.jit.trace(model, example)
traced.save("gu_model.pt")
