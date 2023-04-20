"""
Copyright 2023 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
see the License for the specific language governing permissions and
limitations under the License.
"""

from argparse import ArgumentParser
import os

parser = ArgumentParser()
parser.add_argument('-v', '--version')

version = parser.parse_args().version
assert version is not None

style_changes = "\n\
ul.Versionbox { \n\
    float:left;\n\
    margin:0 25px 0 -25px;\n\
    padding:0;\n\
}\n\
ul.Versionbox li {\n\
    list-style:none;\n\
    float:left;\n\
    padding: 5px 0px;\n\
}\n\
#version {\n\
    background-size:13px;\n\
    background-repeat:no-repeat;\n\
    background-position:2px 3px;\n\
    padding-left:20px;\n\
    position:relative;\n\
    right:-18px;\n\
}"
text_html = "\n\
<ul class=\"Versionbox\">\n\
<li><a href=\"../../../../versions.html\"><span id=\"version\">Version " + version + " ▼</span></a></li>\n\
</ul>"
anchor_text = '<div class="subNav">'

css_filepath = f'./version-{version}/stylesheet.css'
with open(css_filepath, "a") as f:
    f.write(style_changes)

path = f'./version-{version}/com/android/identity'
for filename in os.listdir(path):
    with open(os.path.join(path, filename), 'r') as file:
        data = file.read()
        insert_loc = data.find(anchor_text) + len(anchor_text)
        new_data = data[:insert_loc] + text_html + data[insert_loc:]

    with open(os.path.join(path, filename), 'w') as file:
        file.write(new_data)

index_path = f'./index.html'
reference_index_path = f'./version-{version}/index.html'
with open(reference_index_path, 'r') as file:
    data = file.read()
    new_data = data.replace("com/android", f'version-{version}/com/android')

with open(index_path, 'w') as file:
    file.write(new_data)

versions_path = './versions.html'
new_version_text = f"<a href=\"version-{version}/index.html\">Version {version}</a>\n"
with open(versions_path, 'r') as file:
    data = file.read()
    insert_loc = data.find("</body>")
    new_data = data[:insert_loc] + new_version_text + data[insert_loc:]

with open(versions_path, 'w') as file:
    file.write(new_data)
