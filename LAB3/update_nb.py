import json
with open('lab3.ipynb', 'r', encoding='utf-8') as f:
    nb = json.load(f)

# Find cell with comparison = pd.DataFrame
for cell in nb['cells']:
    if cell['cell_type'] == 'code':
        source = ''.join(cell['source'])
        if 'comparison = pd.DataFrame([' in source:
            new_source = source.replace(
                "        'n_a': n_a, 'n_b': n_b,\n        'weight_a': weight_equal_a, 'weight_b': weight_equal_b,",
                "        'n_a': n_a, 'n_b': n_b,\n        'w_a': w_a, 'b_a': b_a, 'w_b': w_b, 'b_b': b_b,\n        'weight_a': weight_equal_a, 'weight_b': weight_equal_b,"
            ).replace(
                "        'n_a': n_a, 'n_b': n_b,\n        'weight_a': weight_sample_a, 'weight_b': weight_sample_b,",
                "        'n_a': n_a, 'n_b': n_b,\n        'w_a': w_a, 'b_a': b_a, 'w_b': w_b, 'b_b': b_b,\n        'weight_a': weight_sample_a, 'weight_b': weight_sample_b,"
            )
            # Reconstruct list of lines with trailing newlines
            cell['source'] = [line + '\n' for line in new_source.split('\n')]
            # Remove the last trailing newline
            if cell['source']:
                cell['source'][-1] = cell['source'][-1][:-1]

# Find the markdown cell for short comparison and update it
for cell in nb['cells']:
    if cell['cell_type'] == 'markdown':
        source = ''.join(cell['source'])
        if 'Group B has more influence under sample-weighted aggregation' in source:
            new_text = "## 4. Short comparison\n\nGroup B has more influence under sample-weighted aggregation because it contains more training examples ($n_b=8$ versus $n_a=4$). Equal weighting gives both local models the same influence regardless of sample count. The sample-weighted aggregation performs better on the shared test set, achieving a lower test MSE compared to equal-weight aggregation."
            cell['source'] = [line + '\n' for line in new_text.split('\n')]
            if cell['source']:
                cell['source'][-1] = cell['source'][-1][:-1]

with open('lab3.ipynb', 'w', encoding='utf-8') as f:
    json.dump(nb, f, indent=1)
