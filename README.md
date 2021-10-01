# Курс основ программирования на МКН СПбГУ
## Проект 2: key-value база данных

[Постановка задачи](./TASK.md)

## Использование программы

Передавать аргументы при запуске не нужно. Взаимодействие осуществляется интерактивно. При старте будет выведен список команд и их синтаксис, а именно:

    create NAME             to switch to a new empty database NAME
    open NAME               to switch to database NAME from files NAME.index and NAME.content
    store KEY VALUE         to set value for KEY equal to VALUE (overwriting, if needed)
    fetch KEY               to print value for KEY
    append NAME             to add all key-value pairs from database NAME to the current database (overwriting, if needed)
    print                   to print content of the current database
    exit                    to quit program

Пример взаимодействия:

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

## Внутреннее устройство

База данных хранится в двух файлах: NAME.index и NAME.content,
где NAME - имя этой базы данных. В первом лежит хеш-таблица,
по хешу ключа выдающая адреса ключа и значения во втором файле. 
Иными словами, массив троек вида (хеш ключа: Long, адрес ключа: Long, адрес значения: Long).
А во втором файле лежат строки, перед которыми записаны их длины (как это делает RandomAccessFile.writeUTF()).

За счёт подобной структуры можно работать с базой данных, не считывая и не сохраняя целиком весь её файл.
Однако, при этом на каждый запрос приходится читать данные из файла, из-за чего скорость обработки
составляет несколько тысяч штук в секунду.