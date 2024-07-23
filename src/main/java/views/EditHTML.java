package views;

import java.util.Map;

public class EditHTML {

    public static String generateEditHtml(String pid, Map<String, String> files, String editKey, String publicURL) {
        String part1 = """
                <?xml version="1.0" encoding="UTF-8" ?>
                <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head>
                    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
                    <title>Edit Problem</title>
                </head>
                <body style="font-family: sans-serif;">
                Public URL (for your students):
                <a href="%s" target="_blank">%s</a>
                <form method="post" action="/editedFiles/%s/%s">
                    <div>
                """;

        StringBuilder result = new StringBuilder();
        result.append(String.format(part1, publicURL, publicURL, pid, editKey));

        String part2 = """
                <div id="item%d">
                    <p>File name: <input type="text" id="filename%d" name="filename%d" size="25" value="%s"/>
                    <button id="delete%d" type="button">Delete</button>
                    </p>
                    <p><textarea id="contents%d" name="contents%d" rows="24" cols="80">%s</textarea></p>
                </div>
                """;

        int i = 0;
        for (Map.Entry<String, String> entry : files.entrySet()) {
            i++;
            result.append(String.format(part2, i, i, i, entry.getKey(), i, i, i, entry.getValue()));
        }

        String part3 = """
                    <div id="addfilecontainer">Need more files? <button id="addfile" type="button">Add file</button></div>
                    <div><input type="submit" value="Submit changes"/></div>
                    </div>
                </form>
                <p></p>
                <hr/>
                <form method="post" action="/editedProblem/%s/%s" enctype="multipart/form-data">
                <p>Alternatively, upload a zip file. Select the file <input name="file" id="file" type="file"/></p>
                <p><input id="upload" type="submit" value="Upload zip file"/></p></form>
                <script type="text/javascript">
                for (let i = 1; i <= %d; i++) {
                    document.getElementById('delete' + i).addEventListener('click',
                        function() {
                            document.getElementById('filename' + i).setAttribute('value', '')
                            document.getElementById('contents' + i).innerHTML = ''
                            document.getElementById('item' + i).style.display = 'none'
                        })
                }
                let fileIndex = %d
                document.getElementById('addfile').addEventListener('click',
                    function() {
                        fileIndex++
                        let fileDiv = document.createElement('div')
                        fileDiv.setAttribute('id', 'item' + fileIndex)
                        fileDiv.innerHTML = '<p>File name: <input id="filename' + fileIndex + '" name="filename' + fileIndex
                                + '" size="25" type="text"/> <button id="delete' + fileIndex
                                + '" type="button">Delete</button></p><p><textarea id="contents' + fileIndex + '" name="contents' + fileIndex
                                + '" rows="24" cols="80"/></textarea></p>'
                        let addFile = document.getElementById('addfilecontainer')
                        addFile.parentNode.insertBefore(fileDiv, addFile)

                        document.getElementById('delete' + fileIndex).addEventListener('click',
                            function() {
                                document.getElementById('filename' + fileIndex).setAttribute('value', '')
                                document.getElementById('contents' + fileIndex).innerHTML = ''
                                document.getElementById('item' + fileIndex).style.display = 'none'
                        })
                })

                document.getElementById('upload').disabled = document.getElementById('file').files.length === 0

                document.getElementById('file').addEventListener('change', function() {
                    document.getElementById('upload').disabled = document.getElementById('file').files.length === 0
                })
                </script>
                </body>
                </html>
                """;

        result.append(String.format(part3, pid, editKey, files.size(), files.size()));

        return result.toString();
    }
}
