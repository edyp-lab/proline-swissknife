# Isobaric Enumeration 

## Running the tool 

To run the tool use the command name (`isobaric`) and specify the Proline project id (with '-p' option)   

```
run.bat isobaric -p 315
```

## What it does

Consider 3 variants and a canonical form of a peptide: 

| name   | sequence       |
|--------|----------------|
| h3     | KSAPATGGVKKPHR |
| h33    | KSAPSTGGVKKPHR |
| h3mm13 | KSVPSTGGVKKPHR |
| h3mm7  | KSAPSIGGVKKPHR |
| h3mm6  | KSAPTTGGVKKPHR |

Retrieve modification definitions from the Proline database : 

| name                    |
| ----------------------- |
| acetyl (K)              |
| methyl (K)              |
| dimethyl (K)            |
| trimethyl (K)           |
| propionyl (K)           |
| butyryl (K)             |
| crotonyl (K)            |
| lactyl_DP (K)           |
| Hydroxybutyrylation (K) |
| Formyl (K)              |
| Propionyl (ST)          |
| Formyl (ST)             |

For each variant, enumerate possible modifications, then filter combinations that contains more than 1 "defect". we consider that a defect corresponds to one of the following situations : 
 - not all Lysine are modified 
 - no Propionyl Nter
 - presence of a Propionyl S or T
 - presence of a Formyl S or T



Redundant combination of non located modifications are removed (for example (Acetyl, Methyl, Trimethyl) is considered as equal to (Methyl, Trimethyl, Acetyl))