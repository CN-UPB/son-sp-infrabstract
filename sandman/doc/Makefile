


CONFIGDIR     = .
SPHINXOPTS    = -c $(CONFIGDIR)
SPHINXBUILD   = sphinx-build
SPHINXPROJ    = translator
SOURCEDIR     = javasphinx
BUILDDIR      = sphinx


all: javadoc javasphinx/index.rst sphinx/html sphinx/latex

#.PHONY: javasphinx sphinx/html sphinx/latex

# -d output_dir -locale en_US -windowtitle "PG-SanDMAN Translator" -doctitle "foobar" -notimestamp -use
javadoc:
	-javadoc -d javadoc -subpackages sonata.kernel.placement -sourcepath "../placement/src/main/java/" -overview "../placement/src/main/java/overview.html" -bottom "<a href=\"../sphinx/html/index.html\">Sphinx documentation</a>"

# rule to manually recreate sphinx java file index
javasphinx: javasphinx/index.rst
	
javasphinx/index.rst:
	javasphinx-apidoc -f -t "PG-SanDMAN Translator" -o javasphinx "../placement/src/main/java/"
	-mv -f javasphinx/packages.rst javasphinx/index.rst
	sed -i '3i`Javadoc <../../javadoc/index.html>`_' javasphinx/index.rst

sphinx/html: javasphinx/index.rst
	@$(SPHINXBUILD) -M html "$(SOURCEDIR)" "$(BUILDDIR)" $(SPHINXOPTS) $(0)

sphinx/latex: javasphinx/index.rst
	@$(SPHINXBUILD) -M latex "$(SOURCEDIR)" "$(BUILDDIR)" $(SPHINXOPTS) $(0)
	cd sphinx/latex; make; cd ../..

clean:
	-rm -rf javadoc
	-rm -rf javasphinx
	-rm -rf sphinx
