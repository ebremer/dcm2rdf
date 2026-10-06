# Golden-file samples

`GoldenFileTest` converts real DICOM files, written by other software than dcm4che, and checks the result.
The files are third-party data, so they are **not stored in this repository**: the `golden` profile downloads
them into `target/golden-samples` and checks them against the SHA-256 hashes pinned in the test. Without the
profile the tests are skipped.

```
mvn test -Pgolden                          # download (once) and check
mvn test -Pgolden -Dgolden.update=true     # after a change to the output that is meant
```

The expected output isn't committed either, as it would carry the same data: `expected-digests.properties`
holds a digest of each sample's default conversion (`GoldenFileTest.digest`, independent of blank node labels).
An update also saves each output as `target/golden-samples/<name>.accepted.ttl`; a later mismatch then names
the predicates whose triples changed, without printing their values.

| Sample | What it covers |
|---|---|
| `tcia-sts-017-rtstruct` | Implicit VR Little Endian; Specific Character Set ISO_IR 192 (UTF-8) with non-ASCII text; private tags at the top level and inside sequence items; sequences nested four deep; an RT Structure Set with ContourData. The test also re-encodes it, in memory, as Deflated Explicit VR Little Endian. |
| `tcia-cptac-lscc-pet-bigendian` | Explicit VR Big Endian; 171 private elements from four private creators. |

## Sources and licences

Both come from The Cancer Imaging Archive (TCIA), through the NCI Imaging Data Commons (IDC, release v24).
TCIA de-identified them (PS3.15 Annex E; see DeidentificationMethod in each file). Using them is subject to the
TCIA Data Usage Policy, https://www.cancerimagingarchive.net/data-usage-policies-and-restrictions/: do not
attempt to identify or contact the individual participants.

### tcia-sts-017-rtstruct

- Collection: Soft-tissue-Sarcoma, patient STS_017, series "RTstruct_STIR"
- Source: https://idc-open-data.s3.amazonaws.com/cd506b5c-a9b1-4349-b651-1e05fc188028/d7394a95-781a-4425-b2e4-dd48b4d7de17.dcm
- SHA-256: 39fd6445059adaf0ba60f07f2285a09eb8eeb1eeebe4aa96b851f518542e0da2
- Licence: CC BY 3.0, https://creativecommons.org/licenses/by/3.0/
- Citation: Vallières, M., Freeman, C. R., Skamene, S. R., & El Naqa, I. (2015). A radiomics model from joint
  FDG-PET and MRI texture features for the prediction of lung metastases in soft-tissue sarcomas of the
  extremities (Soft-tissue-Sarcoma) [Dataset]. The Cancer Imaging Archive.
  https://doi.org/10.7937/K9/TCIA.2015.7GO2GSKS

### tcia-cptac-lscc-pet-bigendian

- Collection: CPTAC-LSCC, patient C3N-02494, series "WB 3D AC" (one PET slice)
- Source: https://idc-open-data-two.s3.amazonaws.com/752a1df1-1164-41da-87b6-a67d47dd9dce/98ed8478-f042-4c55-9eda-006573ee6b7f.dcm
- SHA-256 as served: a08154d7149e2096ac450714425738828928f9720099e5add57265e10225c1f6
- After download the test removes the GE private element (0009,100E), "Scan Ready Datetime": it holds GE's
  unset timestamp moved by TCIA's date shift, so it reveals the shift and with it the real scan date. SHA-256
  after: c019a30181a2d1249e62e3d7144e35e92ec9c1765949cd467575c895790866f3
- Licence: CC BY 4.0, https://creativecommons.org/licenses/by/4.0/
- Citation: National Cancer Institute Clinical Proteomic Tumor Analysis Consortium (CPTAC). (2018). The Clinical
  Proteomic Tumor Analysis Consortium Lung Squamous Cell Carcinoma Collection (CPTAC-LSCC) (Version 15)
  [Data set]. The Cancer Imaging Archive. https://doi.org/10.7937/K9/TCIA.2018.6EMUB5L2

### Imaging Data Commons

Fedorov, A., et al. (2023). National Cancer Institute Imaging Data Commons: Toward Transparency,
Reproducibility, and Scalability in Imaging Artificial Intelligence. RadioGraphics 43(12).
https://doi.org/10.1148/rg.230180
