# Compiler settings
JAVAC = javac
SRCDIR = .
BUILDDIR = build

# Find all .java files recursively, excluding build directory
SOURCES = $(shell find $(SRCDIR) -name "*.java" -not -path "./build/*" -not path "./Reader")
CLASSES = $(SOURCES:%.java=$(BUILDDIR)/%.class)

# Default target
all: clean compile zip

# Compile Java files
compile:
	@mkdir -p $(BUILDDIR)
	$(JAVAC) -d $(BUILDDIR) $(SOURCES)

# Clean build directory
clean:
	@rm -rf $(BUILDDIR)
	@rm -f CodeCraft.zip

# Package source files
zip:
	@echo "Creating zip archive..."
	@rm -f files.txt
	@find . -name "*.java" -not -path "./build/*" -not -path "./test/*" -not -path "./Reader/*" | sed 's/^.\///' > files.txt
	@7z a -tzip CodeCraft.zip @files.txt
	@rm -f files.txt
	@echo "Package created: CodeCraft.zip"

# Show contents of zip
list:
	@7z l CodeCraft.zip

move:
	mv CodeCraft.zip /mnt/c/Users/28699/Downloads

.PHONY: all clean compile package list
