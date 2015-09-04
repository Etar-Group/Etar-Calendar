import os, fnmatch, re

def findReplace(directory, find, replace, filePattern):
    for path, dirs, files in os.walk(os.path.abspath(directory)):
        for filename in fnmatch.filter(files, filePattern):
            filepath = os.path.join(path, filename)
            print(filepath)
            with open(filepath) as f:
                s = f.read()
            s = re.sub(find, replace, s)
            with open(filepath, "w") as f:
                f.write(s)

# gradle can't compile if two strings are defined with the same name, this removes one useless occurence
findReplace("res", r"(<string name=\"custom\" product=\"tablet\".*>).*(</string>)", r"", "strings.xml")

# change import of generated R file to fix packagename
findReplace("src", r"import com.android.calendar.R;", r"import ws.xsoh.etar.R;", "*.java")

# add R import to com.android.calendar
findReplace("src", r"package com.android.calendar;", r"package com.android.calendar;\n\nimport ws.xsoh.etar.R;", "*.java")
