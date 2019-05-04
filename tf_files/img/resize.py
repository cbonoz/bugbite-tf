#!/usr/bin/env python

from PIL import Image
import os, sys

def resizeImage(infile, output_dir="", size=(224, 224)):
     outfile = os.path.splitext(infile)[0]+""
     extension = os.path.splitext(infile)[1]

     if infile != outfile:
        try:
            im = Image.open(infile)
            im = im.resize(size, Image.ANTIALIAS)
            im.save(outfile+extension)
            print('resizing ' + infile, im.size)
        except IOError as e:
            print(e)
            print("cannot reduce image for ", infile)


if __name__=="__main__":
    dir = os.getcwd()
    output_dir = os.path.join(dir, "new")

    dirs = filter(lambda x: os.path.isdir(x), os.listdir(dir))
    for d in dirs:
        for f in os.listdir(d):
            current_file = os.path.join(dir , "%s/%s" % (d,f))
            if 'resized' in f:
                print(current_file)
                os.remove(current_file)
                continue

            # output_dir = os.path.join(dir, d)
            # print(current_file, output_dir)
            resizeImage(current_file, output_dir)
