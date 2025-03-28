# Compiler settings
JAVAC = javac
SRCDIR = .
BUILDDIR = build

# Find all .java files recursively, excluding build directory
SOURCES = $(shell find $(SRCDIR) -name "*.java" -not -path "./build/*")
CLASSES = $(SOURCES:%.java=$(BUILDDIR)/%.class)

# Default target
all: clean compile package

# Compile Java files
compile:
	@mkdir -p $(BUILDDIR)
	$(JAVAC) -d $(BUILDDIR) $(SOURCES)

# Clean build directory
clean:
	@rm -rf $(BUILDDIR)
	@rm -f CodeCraft.tar

# Package source files
package:
	@echo "Creating tar archive..."
	@find . -name "*.java" \
		-not -path "./build/*" \
		-not -path "./test/*" \
		| tar -czf CodeCraft.tar -T -
	@echo "Package created: CodeCraft.tar"

# Show contents of tar
list:
	tar -tvf CodeCraft.tar

.PHONY: all clean compile package list