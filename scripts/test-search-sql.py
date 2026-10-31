#!/usr/bin/env python3
"""Execute the actual @Query SQL from source against host SQLite; not an Android Room runtime test."""
import ast
import pathlib
import re
import sqlite3
import unittest
source = (pathlib.Path(__file__).resolve().parent.parent / 'app/src/main/java/pt/atlas/personal/data/AtlasDatabase.kt').read_text()
queries = {}
for match in re.finditer(r'@Query\("((?:\\.|[^"\\])*)"\)\s*(?:suspend\s+)?fun\s+(\w+)', source):
    queries[match[2]] = ast.literal_eval('"' + match[1] + '"')

class ActualSearchQueries(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        self.db.execute('CREATE TABLE records(id TEXT,kind TEXT,title TEXT,body TEXT,tags TEXT,createdAt INTEGER,dueAt INTEGER,updatedAt INTEGER)')
        self.db.execute('CREATE TABLE sections(id TEXT,documentId TEXT,page INTEGER,text TEXT)')
    def tearDown(self):
        self.db.close()
    def test_literal_wildcard_is_not_a_wildcard(self):
        self.db.executemany('INSERT INTO records VALUES(?,?,?,?,?,?,?,?)', [('a','note','100% real','texto','',1,None,1),('b','note','1000 real','texto','',1,None,1)])
        sql = queries['search'].replace('IN (:kinds)', 'IN (:kind)')
        found = self.db.execute(sql, dict(pattern=r'%100\%%', kind='note', limit=12)).fetchall()
        self.assertEqual(['a'], [r[0] for r in found])
    def test_chunk_is_retrieved_near_match_not_only_start_of_document(self):
        self.db.execute('INSERT INTO sections VALUES(?,?,?,?)', ('s','d',4,'x '*2000 + 'fotossíntese nas plantas' + ' y'*1000))
        found = self.db.execute(queries['evidenceSections'], dict(term='fotossíntese', pattern='%fotossíntese%', limit=8)).fetchone()
        self.assertEqual(4, found[2])
        self.assertIn('fotossíntese', found[3])
        self.assertLessEqual(len(found[3]), 1600)
    def test_old_section_search_escape_is_valid(self):
        self.db.execute('INSERT INTO sections VALUES(?,?,?,?)', ('s','d',1,'100% exemplo'))
        self.assertEqual(1, len(self.db.execute(queries['searchSections'], dict(pattern=r'%100\%%',limit=8)).fetchall()))
if __name__ == '__main__':
    unittest.main(verbosity=2)
