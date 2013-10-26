import os, fnmatch, re

def findReplace(directory, filePattern):
    for path, dirs, files in os.walk(os.path.abspath(directory)):
        for filename in fnmatch.filter(files, filePattern):
            filepath = os.path.join(path, filename)
            print(filepath)
            with open(filepath) as f:
                s = f.read()
            s = re.sub(r'(<string name=\"custom\" product=\"tablet\".*>).*(</string>)', r'', s)
            with open(filepath, "w") as f:
                f.write(s)

findReplace("res", "strings.xml")
