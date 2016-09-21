#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

"""Simple sSQL test"""

from datetime import datetime
from pyspark.sql import Row
from sparktestingbase.sqltestcase import SQLTestCase
import unittest2


class SimpleSQLTest(SQLTestCase):
    """A simple test."""

    def test_simple_expected_equal(self):
        allTypes = self.sc.parallelize([Row(
            i=1, s="string", d=1.0, l=1,
            b=True, list=[1, 2, 3], dict={"s": 0}, row=Row(a=1),
            time=datetime(2014, 8, 1, 14, 1, 5))])
        df = allTypes.toDF()
        self.assertDataFrameEqual(df, df)

    def test_simple_close_equal(self):
        allTypes1 = self.sc.parallelize([Row(
            i=1, s="string", d=1.0, l=1,
            b=True, list=[1, 2, 3], dict={"s": 0}, row=Row(a=1),
            time=datetime(2014, 8, 1, 14, 1, 5))])
        allTypes2 = self.sc.parallelize([Row(
            i=1, s="string", d=1.001, l=1,
            b=True, list=[1, 2, 3], dict={"s": 0}, row=Row(a=1),
            time=datetime(2014, 8, 1, 14, 1, 5))])
        self.assertDataFrameEqual(allTypes1.toDF(), allTypes2.toDF(), 0.1)

    @unittest2.expectedFailure
    def test_simple_close_unequal(self):
        allTypes1 = self.sc.parallelize([Row(
            i=1, s="string", d=1.0, l=1,
            b=True, list=[1, 2, 3], dict={"s": 0}, row=Row(a=1),
            time=datetime(2014, 8, 1, 14, 1, 5))])
        allTypes2 = self.sc.parallelize([Row(
            i=1, s="string", d=1.001, l=1,
            b=True, list=[1, 2, 3], dict={"s": 0}, row=Row(a=1),
            time=datetime(2014, 8, 1, 14, 1, 5))])
        self.assertDataFrameEqual(allTypes1.toDF(), allTypes2.toDF(), 0.0001)

    @unittest2.expectedFailure
    def test_very_simple_close_unequal(self):
        allTypes1 = self.sc.parallelize([Row(d=1.0)])
        allTypes2 = self.sc.parallelize([Row(d=1.001)])
        self.assertDataFrameEqual(allTypes1.toDF(), allTypes2.toDF(), 0.0001)

    @unittest2.expectedFailure
    def test_dif_schemas_unequal(self):
        allTypes1 = self.sc.parallelize([Row(d=1.0)])
        allTypes2 = self.sc.parallelize([Row(d="1.0")])
        self.assertDataFrameEqual(allTypes1.toDF(), allTypes2.toDF(), 0.0001)

if __name__ == "__main__":
    unittest2.main()
