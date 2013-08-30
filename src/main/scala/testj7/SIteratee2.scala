package testj7

import fj.control.Trampoline
import fj.data.IO
import fj.data.Iteratee
import Iteratee._
import IterV._
import Input._

import org.supercsv.io.CsvBeanWriter
import org.supercsv.io.ICsvBeanWriter
import org.supercsv.prefs.CsvPreference

import java.io.FileWriter
import java.io.IOException
import java.io.Writer
import java.nio.file.Paths

object SIteratee2 extends App {

  /**
   * Enumerator that feeds an Iteratee from a Stream
   */
  def enumerate[E, A](xs: Stream[E], it: IterV[E, A]): Trampoline[IterV[E, A]] =
    if (xs.isEmpty) Trampoline.pure(it)
    else Trampoline.suspend { () =>
      it.cfold { _ => _ => Trampoline.pure(it) } { fi => enumerate(xs.tail, fi(el(xs.head))) }
    }

  // ################ Simple example

  def counter: IterV[String, Int] = {
    def step(n: Int): Input[String] => IterV[String, Int] =
      in => in.capply { cont(step(n)) } { _ => cont(step(n + 1)) } { done(n, eof()) }
    cont(step(0))
  }

  val stringSource: Stream[String] = "toto" #:: "tutu" #:: "titi" #:: stringSource

  // Constant stack AND heap
  //println("Result : %s".format(enumerate(stringSource.take(1000000000), counter).run().run().toString))  

  // ################ Advanced example  

  trait SuperCSV[A] { self =>
    def run: A

    def superCSV[B](b: B) = new SuperCSV[B] {
      def run = b
    }

    def bind[B](f: A => SuperCSV[B]) = new SuperCSV[B] {
      def run = f(self.run).run
    }

    def map[B](f: A => B) = bind(a => superCSV(f(a)))
  }

  def trampCont[T](fi: => (Input[T] => Trampoline[IterV[T, SuperCSV[Unit]]])) = {
    Trampoline.suspend(() => Trampoline.pure(cont(fi andThen (_.run))))
  }

  def write[T](writer: ICsvBeanWriter, headers: Array[String], mappings: Array[String]): IterV[T, SuperCSV[Unit]] = {
    def step(csv: => SuperCSV[ICsvBeanWriter]): Input[T] => IterV[T, SuperCSV[Unit]] = {
      lazy val empty = cont(step(csv))
      lazy val el = (t: T) => cont(step(csv bind {
        w =>
          new SuperCSV[ICsvBeanWriter] {
            def run = {
              w.write(t, mappings: _*)
              w.flush()
              w
            }
          }
      }))
      lazy val eof = done(csv map (w => ()), Input.eof[T]())

      in => in.capply(empty)(el)(eof)
    }

    cont(step(new SuperCSV[ICsvBeanWriter] {
      def run = {
        writer.writeHeader(headers: _*)
        writer
      }
    }))
  }

  class Person(name: String, firstname: String) {
    def getName = name
    def getFirstname = firstname
  }

  object Durand extends Person("Durand", "Joel")
  object Dupont extends Person("Dupont", "Marcel")
  object Duchmol extends Person("Duchmol", "Gontran")

  val persSource: Stream[Person] = Durand #:: Dupont #:: Duchmol #:: persSource

  var w: Writer = _
  try {
    w = new FileWriter(Paths.get("/tmp/test.csv").toFile())
    val iw = new CsvBeanWriter(w, CsvPreference.EXCEL_NORTH_EUROPE_PREFERENCE)
    enumerate(persSource.take(25000000), write[Person](iw, Array("FIRSTNAME", "NAME"), Array("firstname", "name"))).run.run.run
  } catch {
    case e: IOException => e.printStackTrace()
  } finally {
    w.close()
  }

}
