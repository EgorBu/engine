package tech.sourced.engine.provider

import org.apache.hadoop.fs.{FileSystem, Path}
import org.eclipse.jgit.lib.ObjectId
import org.scalatest.{FlatSpec, Matchers}
import tech.sourced.engine.{BaseSivaSpec, BaseSparkSpec}

import scala.collection.JavaConverters._

class RepositoryProviderSpec extends FlatSpec with Matchers with BaseSivaSpec with BaseSparkSpec {
  "SivaRDDProvider" should "return always the same instance" in {
    val prov = SivaRDDProvider(ss.sparkContext)
    val prov2 = SivaRDDProvider(ss.sparkContext)

    prov should equal(prov2)
    prov should not equal new SivaRDDProvider(ss.sparkContext)

  }

  "SivaRDDProvider" should "return the exact name of siva files" in {
    val prov = SivaRDDProvider(ss.sparkContext)

    val sivaRDD = prov.get(resourcePath)

    sivaRDD.count() should be(3)
  }

  "RepositoryProvider" should "read correctly siva repositories" in {
    val prov = SivaRDDProvider(ss.sparkContext)

    val sivaRDD = prov.get(resourcePath)

    val refs = sivaRDD.flatMap(pds => {
      val repo = RepositoryProvider("/tmp").get(pds)

      repo.getAllRefs.asScala.mapValues(r => ObjectId.toString(r.getPeeledObjectId))
    }).collect()

    refs.length should be(56)
  }

  "RepositoryProvider" should "not delete siva file with skipCleanup = true" in {
    val prov = SivaRDDProvider(ss.sparkContext)
    val sivaRDD = prov.get(resourcePath)
    val sivaFilesExist = sivaRDD.map(pds => {
      val provider = RepositoryProvider("/tmp", skipCleanup = true)
      val repo = provider.get(pds)
      val localSivaPath = new Path("/tmp",
        new Path(RepositoryProvider.temporalSivaFolder, pds.getPath()))
      provider.close(pds, repo)
      FileSystem.get(pds.getConfiguration).exists(localSivaPath)
    }).collect()

    assert(sivaFilesExist.length == 3)
    assert(sivaFilesExist.forall(_ == true))
  }

  "RepositoryProvider" should "delete siva file with skipCleanup = false" in {
    val prov = SivaRDDProvider(ss.sparkContext)

    val sivaRDD = prov.get(resourcePath)

    val sivaFilesExist = sivaRDD.map(pds => {
      val provider = new RepositoryProvider("/tmp/two")
      val repo = provider.get(pds)
      provider.close(pds, repo)
      val localSivaPath = new Path("/tmp/two",
        new Path(RepositoryProvider.temporalSivaFolder, new Path(pds.getPath()).getName))
      FileSystem.get(pds.getConfiguration).exists(localSivaPath)
    }).collect()

    assert(sivaFilesExist.length == 3)
    assert(sivaFilesExist.forall(!_))
  }

  "RepositoryProvider" should "cleanup unpacked files when nobody else is using the repo" in {
    val prov = SivaRDDProvider(ss.sparkContext)
    val sivaRDD = prov.get(resourcePath)
    val pds = sivaRDD.first()

    // needs to be a fresh instance, since some of the tests may not cleanup
    val provider = new RepositoryProvider("/tmp/cleanup-test-" + System.currentTimeMillis())

    val repo1 = provider.get(pds)
    val fs = FileSystem.get(pds.getConfiguration)
    val repo2 = provider.get(pds)

    provider.close(pds, repo1)
    fs.exists(new Path(repo1.getDirectory.toString)) should be(true)

    provider.close(pds, repo2)
    fs.exists(new Path(repo2.getDirectory.toString)) should be(false)
  }

  "RepositoryProvider with skipCleanup = true"
    .should("not cleanup unpacked files when nobody else is using the repo").in({
    val prov = SivaRDDProvider(ss.sparkContext)
    val sivaRDD = prov.get(resourcePath)
    val pds = sivaRDD.first()

    // needs to be a fresh instance, since some of the tests may not cleanup
    val provider = new RepositoryProvider("/tmp/cleanup-test-"
      + System.currentTimeMillis(), skipCleanup = true)

    val repo = provider.get(pds)
    val fs = FileSystem.get(pds.getConfiguration)
    val repo2 = provider.get(pds)

    provider.close(pds, repo)
    repo.getDirectory.toPath
    fs.exists(new Path(repo.getDirectory.toString)) should be(true)

    provider.close(pds, repo2)
    fs.exists(new Path(repo2.getDirectory.toString)) should be(true)
  })

}
