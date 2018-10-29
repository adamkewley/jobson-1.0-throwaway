Install
=======

The Jobson project builds distributions (unix, debian) of ``jobson``
that include the Jobson's core libraries, documentation, UI assets,
etc.


Debian OSes (Ubuntu, Mint, etc.)
--------------------------------

The ``jobson`` build produces a ``.deb`` file that can be installed
directly:

.. code:: bash

	  apt install openjdk-8-jre
	  wget JOBSON DEB
	  dpkg -i JOBSON DEB

	  
OSX/Linux (any)
---------------

The ``jobson`` project produces a tarball containing all assets needed
to run jobson on a unix-like OS.

**Note**: You must have ``java`` (8+) installed on your system in order to run ``jobson``

.. code:: bash

	  wget JOBSON_NIX
	  tar xvf JOBSON_NIX
	  bin JOBSON_NIX/bin/jobson

	  # optional: softlink into /usr/local/bin, so it's on the path
	  ln -s JOBSON_NIX/bin/jobson /usr/local/bin/jobson

	  # (or) optional: add to ~/.bashrc
	  export PATH=$PATH:/location/of/unpacked/tarball/bin

Docker
------

The ``jobson`` project builds a Docker image that pre-integrates
``jobson`` and ``jobson-ui`` (with ``nginx``) together.

TODO: docker details.

	  
Windows
-------

The ``jobson`` project does not officially build or support
windows. However, unofficial reports do state that it works on windows
if you manually run the ``jobson-x.x.x.jar`` file:

* Download and install ``java``
* Download jobson unix tarball from releases (TODO)
* Unpack somewhere (e.g. DIR TODO)
* Run the jobson CLI with ``java -jar DIR/share/jobson/java/jobson-x.x.x.jar CMD``
