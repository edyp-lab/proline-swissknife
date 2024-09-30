# proline-swissknife



## Getting started 

A set of tools more or less based on proline. Use the `run.bat` batch file to run the available commands. From Windows command prompt (cmd) move to the proline-swissknife folder `cd proline-swissknife-1.1.2-SNAPSHOT`. 

To display the list of commands (and their parameters) type:\
* `run.bat fwhm --help` for commands applying to fwhm statistics
* `run.bat fwhm --help` for commands applying to fwhm statistics



## FWHM commands examples

To extract full width half middle information from Proline quantified dataset, type:

```
run.bat fwhm -p 123 -d 1245 
```

Where -p is the Proline project Id and -d the id of the quantification dataset.  


## isobaric Enumeration command examples

To enumerate isobaric combination of PTMs on Histone variants, type:

```
run.bat isobaric -p 123 
```

Where -p is the Proline project Id where PTMs definitions will be searched.
