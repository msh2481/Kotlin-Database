# Basics of programming course: key-value database

## Running of the database

No command-line arguments are needed, all commands are read interactively. After launch a help text with all supported commands and their syntax will be printed.

    create NAME             to switch to a new empty database NAME
    open NAME               to switch to database NAME from files NAME.index and NAME.content
    store KEY VALUE         to set value for KEY equal to VALUE (overwriting, if needed)
    fetch KEY               to print value for KEY
    append NAME             to add all key-value pairs from database NAME to the current database (overwriting, if needed)
    print                   to print content of the current database
    exit                    to quit program

Example of an interaction:

    $ pf-2021-kvdb-msh2481.bat
    > create db1
    > store a 1
    > store 3 c
    > store abacaba abracadabra
    > fetch a
    1
    > fetch b
    
    > fetch abacaba
    abracadabra
    > exit

## Internal design

A database NAME is stored in two files: NAME.index and NAME.content.
First one is basically an open addressing hash table mapping key hashes to addresses of key and value in second file,
and second is just sequence of strings with length written before each string (as RandomAccessFile.writeUTF() does).

Using such structure makes possible working with database without reading the whole file, but because each operation is
reading from file (that is, from disk), speed is much lower than that of hash tables located in RAM, namely, a few thousands
queries per second.
